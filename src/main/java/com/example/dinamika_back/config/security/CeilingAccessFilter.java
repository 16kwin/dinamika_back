package com.example.dinamika_back.config.security;

import com.example.dinamika_back.features.CeilingParam;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.FeatureSnapshot;
import com.example.dinamika_back.features.journal.FeatureJournal;
import com.example.dinamika_back.features.net.IpNetwork;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Слой правил потолка (пакет B5) поверх прежних правил SecurityConfig. Стоит в цепочке Spring Security после
 * AuthorizationFilter: сюда доходят только запросы, которые прежние правила уже пропустили, поэтому слой может
 * только сузить доступ, но не расширить его. Отказ прежних правил остаётся прежним (те же 403).
 *
 * <p>Что сужает, по порядку:
 * <ol>
 *   <li><b>Роли функций</b> ({@code awms.features.<key>.roles}): путь функции, её детей и функций из requires —
 *       только этим ролям. GET — это и HEAD. Колбэки внешней системы заказов ролями не закрываются (их защищает
 *       ключ OrdersCallbackKeyFilter). Аноним — 401, вошедший с другой ролью — 403;</li>
 *   <li><b>Анонимный доступ</b> (функция {@code anonymous} выключена): без входа остаются только служебные адреса
 *       ({@link PublicEndpoints#always()}) и адреса внешних систем ({@link PublicEndpoints#machine}) — 401;</li>
 *   <li><b>Сети анонимного доступа</b> ({@code awms.security.anonymous.allowed-networks}): анонимный запрос
 *       с адреса вне списка — 401. Адрес клиента за nginx — {@link ClientAddress} (только от доверенных прокси).</li>
 * </ol>
 * Вход, выход, {@code /csrf}, {@code /api/auth/*}, {@code /api/features/public} и {@code /error} работают всегда и
 * с любого адреса.
 *
 * <p>Без файла потолка и при пресете full без ролей и сетей слой ничего не меняет (AccessMatrixFullTest,
 * AccessMatrixNoCeilingTest). Правила строятся один раз при старте по потолку; переключатели администратора (этап 2)
 * их не меняют — выключенную ими функцию закрывают ворота (404 до Security).
 *
 * <p>Попутно ставит атрибут запроса {@link FeatureJournal#CLIENT_IP_ATTRIBUTE} — адрес клиента для журналов.
 */
public class CeilingAccessFilter extends OncePerRequestFilter {

    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("AWMS.SECURITY");
    /** Отказ с одного адреса по одной причине пишется в лог не чаще раза в 10 минут */
    private static final long LOG_INTERVAL_MS = 10 * 60 * 1000L;
    private static final int LOG_KEYS_MAX = 1000;

    private final FeatureSnapshot ceiling;
    private final RequestMatcher always;
    private final RequestMatcher machine;
    private final List<PublicEndpoints.RoleRule> roleRules;
    private final boolean anonymousOn;
    private final List<IpNetwork> anonymousNetworks;
    private final ClientAddress clientAddress;
    private final Map<String, Long> lastLogged = new ConcurrentHashMap<>();

    public CeilingAccessFilter(FeatureSnapshot ceiling) {
        this.ceiling = ceiling;
        this.always = new OrRequestMatcher(PublicEndpoints.always().stream().map(PublicEndpoints.Endpoint::matcher).toList());
        List<PublicEndpoints.Endpoint> machineEndpoints = PublicEndpoints.machine(ceiling);
        this.machine = machineEndpoints.isEmpty() ? request -> false
                : new OrRequestMatcher(machineEndpoints.stream().map(PublicEndpoints.Endpoint::matcher).toList());
        this.roleRules = PublicEndpoints.roleRules(ceiling);
        this.anonymousOn = ceiling.isOn(Feature.ANONYMOUS);
        this.anonymousNetworks = ClientAddress.networks(ceiling, CeilingParam.ANONYMOUS_ALLOWED_NETWORKS);
        this.clientAddress = ClientAddress.of(ceiling);
    }

    /** Слой что-то сужает: есть роли функций, выключен анонимный доступ или заданы его сети (для лога и отчёта) */
    public boolean narrows() {
        return !roleRules.isEmpty() || !anonymousOn || !anonymousNetworks.isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ClientAddress.Resolved client = clientAddress.resolve(request);
        if (client.known()) request.setAttribute(FeatureJournal.CLIENT_IP_ATTRIBUTE, client.text());

        if (!always.matches(request)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            boolean anonymous = isAnonymous(authentication);
            boolean machineCall = machine.matches(request);

            if (!machineCall) {
                for (PublicEndpoints.RoleRule rule : roleRules) {
                    if (rule.matcher().matches(request)
                            && !PublicEndpoints.rolesAllow(ceiling, rule.feature(), authentication)) {
                        String reason = "роли функции " + rule.feature().key() + " в потолке";
                        if (anonymous) {
                            deny(request, response, client, reason);
                            return;
                        }
                        log(request, client, reason + ", пользователь " + forLog(authentication.getName()));
                        throw new AccessDeniedException("Функция " + rule.feature().key() + " недоступна этой роли");
                    }
                }
            }
            if (anonymous) {
                if (!anonymousOn && !machineCall) {
                    deny(request, response, client, "анонимный доступ выключен (anonymous)");
                    return;
                }
                if (!anonymousNetworks.isEmpty() && !inNetworks(client)) {
                    deny(request, response, client, "адрес вне " + CeilingParam.ANONYMOUS_ALLOWED_NETWORKS.key());
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    /** Аноним: входа нет. Отказ — 401: нужно войти (фронтенд обновит токен или откроет вход) */
    private void deny(HttpServletRequest request, HttpServletResponse response, ClientAddress.Resolved client,
                      String reason) throws IOException {
        log(request, client, reason);
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private boolean inNetworks(ClientAddress.Resolved client) {
        if (!client.known()) return false;
        for (IpNetwork network : anonymousNetworks) {
            if (network.contains(client.address())) return true;
        }
        return false;
    }

    static boolean isAnonymous(Authentication authentication) {
        return authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;
    }

    /** Отказ — в лог AWMS.SECURITY, не чаще раза в 10 минут на пару «адрес, причина»: опрос табло не затопит лог */
    private void log(HttpServletRequest request, ClientAddress.Resolved client, String reason) {
        String key = client.text() + "|" + reason;
        long now = System.currentTimeMillis();
        Long last = lastLogged.get(key);
        if (last != null && now - last < LOG_INTERVAL_MS) return;
        if (lastLogged.size() >= LOG_KEYS_MAX) lastLogged.clear();
        lastLogged.put(key, now);
        SECURITY_LOG.warn("Правила потолка: отклонён {} {} {} — {} (следующие такие отказы с этого адреса "
                        + "в ближайшие 10 минут в лог не пишутся)", forLog(request.getMethod()),
                forLog(request.getRequestURI()), client.known() ? "с адреса " + client.text()
                        : "с неизвестного адреса (" + client.text() + ")", reason);
    }

    private static String forLog(String value) {
        return SecurityRoleReadOnlyFilter.forLog(value);
    }
}
