package com.example.dinamika_back.config.security;

import com.example.dinamika_back.features.FeatureResolver;
import com.example.dinamika_back.features.FeatureService;
import com.example.dinamika_back.features.FeatureSnapshot;
import com.example.dinamika_back.features.FeatureWebConfig;
import com.example.dinamika_back.features.FeaturesController;
import com.example.dinamika_back.service.AuthService;
import com.example.dinamika_back.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Строгий потолок завода поверх full: анонимный доступ выключен, роли у выгрузок и панелей, сети станций заданы.
 * По всей таблице {@link AccessMatrix} слой потолка только сужает: каждая изменившаяся ячейка была «прошёл» (200)
 * и стала отказом (401 анониму, 403 вошедшему или 403 CSRF — исключение CSRF у панелей и заказов есть только
 * в режиме табло). Ни один прежний отказ не стал пропуском. Вошедшим меняются только пути функций с ролями,
 * у ADMIN (есть во всех ролях потолка) не меняется ничего; служебные адреса открыты анониму, как раньше.
 */
@WebMvcTest(controllers = FeaturesController.class)
// Тысячи запросов: печать каждого при сбое затопила бы лог
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({SecurityConfig.class, FeatureService.class, SecurityProbe.class, FeatureWebConfig.class})
@TestPropertySource(properties = {
        "awms.features.preset=full",
        "awms.features.anonymous.enabled=false",
        "awms.features.export.roles=ADMIN",
        "awms.features.dashboards.roles=ADMIN,DIRECTOR",
        "awms.security.anonymous.allowed-networks=10.0.5.0/24"})
class AccessMatrixStrictTest {

    private static final Set<String> ANONYMOUS = Set.of("anon", "ext");
    private static final Set<String> NARROWED = Set.of("401", "403");
    /** Прежние исключения CSRF ради режима табло: без него изменения здесь — только с токеном */
    private static final List<String> BOARD_CSRF = List.of("/api/orders/**", "/api/tkp/**", "/api/dashboard/**");
    private static final AntPathMatcher ANT = new AntPathMatcher();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private Environment environment;

    @MockBean
    private AuthService authService;
    @MockBean
    private RefreshTokenService refreshTokenService;

    @Test
    void strictCeilingOnlyNarrows() throws Exception {
        FeatureSnapshot ceiling = FeatureResolver.resolve(environment);
        RequestMatcher always = new OrRequestMatcher(PublicEndpoints.always().stream()
                .map(PublicEndpoints.Endpoint::matcher).toList());
        RequestMatcher roles = new OrRequestMatcher(PublicEndpoints.roleRules(ceiling).stream()
                .map(PublicEndpoints.RoleRule::matcher).toList());

        Map<String, String> golden = AccessMatrix.golden();
        Map<String, String> table = AccessMatrix.table(mvc);
        List<String> widened = new ArrayList<>();
        List<String> unexpected = new ArrayList<>();
        int narrowed = 0;
        for (Map.Entry<String, String> e : golden.entrySet()) {
            String now = table.get(e.getKey());
            if (now == null) continue;
            String[] request = e.getKey().split(" ", 2);
            MockHttpServletRequest probe = new MockHttpServletRequest(request[0], request[1]);
            probe.setServletPath(request[1]);
            Map<String, String> before = cells(e.getValue());
            Map<String, String> after = cells(now);
            for (Map.Entry<String, String> cell : before.entrySet()) {
                String who = cell.getKey().substring(0, cell.getKey().indexOf('#'));
                String was = cell.getValue();
                String is = after.get(cell.getKey());
                if (was.equals(is)) {
                    // Анониму без входа остались только служебные адреса
                    if (ANONYMOUS.contains(who) && "200".equals(was) && !always.matches(probe)) {
                        unexpected.add(e.getKey() + " " + cell.getKey() + ": осталось 200 анониму");
                    }
                    continue;
                }
                if (!"200".equals(was) || !NARROWED.contains(is)) {
                    widened.add(e.getKey() + " " + cell.getKey() + ": было " + was + ", стало " + is);
                    continue;
                }
                narrowed++;
                // Изменилась только ячейка без CSRF-токена, с токеном ответ прежний: исключение CSRF у заказов и
                // панелей было ради режима табло — без него изменения идут с токеном (Axios ставит его сам)
                boolean csrfOnly = cell.getKey().endsWith("#1") && before.containsKey(who + "#2")
                        && before.get(who + "#2").equals(after.get(who + "#2")) && "403".equals(is)
                        && BOARD_CSRF.stream().anyMatch(p -> ANT.match(p, request[1]));
                if (ANONYMOUS.contains(who)) {
                    if (always.matches(probe)) unexpected.add(e.getKey() + " " + cell.getKey() + ": служебный адрес закрыт");
                } else if (!csrfOnly && (who.equals("ADMIN") || !roles.matches(probe))) {
                    unexpected.add(e.getKey() + " " + cell.getKey() + ": вошедшему закрыт путь без ролей потолка");
                }
            }
        }
        assertThat(widened).as("ячейки, где слой потолка расширил доступ или ответил не отказом").isEmpty();
        assertThat(unexpected).as("ячейки, изменившиеся не там, где сужает потолок").isEmpty();
        // Слой действительно работает: справочники, табло, панели и выгрузки закрыты
        assertThat(narrowed).isGreaterThan(300);
        assertThat(table.get("GET /api/nomenclature/tree")).contains("anon=401 ext=401 OPERATOR=200");
        assertThat(table.get("GET /api/dashboard/control-events")).contains("anon=401 ext=401 OPERATOR=403 DIRECTOR=200 ADMIN=200");
        assertThat(table.get("GET /api/features/public")).contains("anon=200 ext=200");
    }

    /** «anon=403|200 OPERATOR=200» → {anon#1=403, anon#2=200, OPERATOR#1=200} */
    private static Map<String, String> cells(String row) {
        Map<String, String> cells = new LinkedHashMap<>();
        String[] parts = row.split(" ");
        for (int i = 2; i < parts.length; i++) {
            int eq = parts[i].indexOf('=');
            String who = parts[i].substring(0, eq);
            String[] values = parts[i].substring(eq + 1).split("\\|");
            for (int v = 0; v < values.length; v++) cells.put(who + "#" + (v + 1), values[v]);
        }
        return cells;
    }
}
