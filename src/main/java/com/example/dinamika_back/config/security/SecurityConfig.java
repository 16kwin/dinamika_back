// SecurityConfig.java — ПОЛНЫЙ ФАЙЛ (добавлены /api/employees/**)
package com.example.dinamika_back.config.security;

import com.example.dinamika_back.features.CeilingParam;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.FeatureConfigurationException;
import com.example.dinamika_back.features.FeatureGateFilter;
import com.example.dinamika_back.features.FeatureResolver;
import com.example.dinamika_back.features.FeatureSnapshot;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.example.dinamika_back.service.AuthService;
import com.example.dinamika_back.service.RefreshTokenService;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Правила доступа (раздел 6.6 проекта, пакет B5). Потолок функций читается статически через
 * {@link FeatureResolver#resolve(Environment)}: срезам {@code @WebMvcTest} новые бины не нужны. Правила строятся
 * один раз при старте, переключатели администратора (этап 2) их не меняют — выключенную ими функцию закрывают
 * ворота (404 до Security).
 *
 * <p>Порядок правил (действует первое совпавшее):
 * <ol>
 *   <li>всегда без входа: вход, ошибки, выход, CSRF-токен, флаги функций до входа;</li>
 *   <li>внешние системы без входа: колбэки заказов из реестра (ключ проверяет OrdersCallbackKeyFilter), приём
 *       тестовой синхронизации;</li>
 *   <li>роли функций из потолка ({@code awms.features.<key>.roles}): совпавшие правила всех функций проверяются
 *       вместе, роли родителя и requires — тоже; GET — это и HEAD;</li>
 *   <li>только после входа: 3D-модели, план цеха, ГПП;</li>
 *   <li>режим табло ({@code anonymous}): прежняя анонимная поверхность;</li>
 *   <li>справочники и всё остальное — после входа, изменения — с CSRF-токеном.</li>
 * </ol>
 * Роль SECURITY вне профиля безопасности только читает ({@link SecurityRoleReadOnlyFilter}).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("AWMS.SECURITY");

    private AuthService authService;
    private Environment environment;

    private @Value("${jwt.access-token-name}") String accessTokenCookieName;
    private @Value("${jwt.refresh-token-name}") String refreshTokenCookieName;
    private @Value("${jwt.access-token-expiration-minutes}") String accessTokenDuration;
    private @Value("${jwt.refresh-token-expiration-minutes}") String refreshTokenDuration;
    private @Value("${ip_address}") String ip_address;

    @Autowired
    public void setUserService(AuthService authService) {
        this.authService = authService;
    }

    @Autowired
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public TokenCookieJweStringSerializer tokenCookieJweStringSerializer(
            @Value("${jwt.cookie-token-key}") String cookieTokenKey
    ) throws Exception {
        return new TokenCookieJweStringSerializer(new DirectEncrypter(
                OctetSequenceKey.parse(cookieTokenKey)
        ));
    }

    @Bean
    public TokenCookieAuthenticationConfigurer tokenCookieAuthenticationConfigurer(
            @Value("${jwt.cookie-token-key}") String cookieTokenKey,
            RefreshTokenService refreshTokenService) throws Exception {
        return new TokenCookieAuthenticationConfigurer(accessTokenCookieName, refreshTokenCookieName, refreshTokenService)
                .tokenCookieStringDeserializer(new TokenCookieJweStringDeserializer(
                        new DirectDecrypter(
                                OctetSequenceKey.parse(cookieTokenKey)
                        )
                ));
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");
        return repository;
    }

    @Bean
    public GetCsrfTokenFilter getCsrfTokenFilter(CsrfTokenRepository csrfTokenRepository) {
        GetCsrfTokenFilter filter = new GetCsrfTokenFilter();
        filter.setCsrfTokenRepository(csrfTokenRepository);
        return filter;
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider();
        daoAuthenticationProvider.setPasswordEncoder(passwordEncoder());
        daoAuthenticationProvider.setUserDetailsService(authService);
        return daoAuthenticationProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            TokenCookieAuthenticationConfigurer tokenCookieAuthenticationConfigurer,
            TokenCookieSessionAuthenticationStrategy tokenCookieSessionAuthenticationStrategy,
            GetCsrfTokenFilter getCsrfTokenFilter,
            CsrfTokenRepository csrfTokenRepository) throws Exception {

        FeatureSnapshot ceiling = FeatureResolver.resolve(environment);
        SECURITY_LOG.info("Правила доступа: режим табло (anonymous) {}, без входа — {} правил(а), роли функций — {} "
                        + "правил(а), CORS: {}", ceiling.isOn(Feature.ANONYMOUS) ? "включён" : "выключен",
                PublicEndpoints.withoutSignIn(ceiling).size(), PublicEndpoints.roleRules(ceiling).size(),
                ceiling.param(CeilingParam.CORS_ALLOWED_ORIGINS).isEmpty() ? "только тот же источник"
                        : ceiling.param(CeilingParam.CORS_ALLOWED_ORIGINS));

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .addFilterAfter(getCsrfTokenFilter, ExceptionTranslationFilter.class)
            .authorizeHttpRequests(auth -> authorize(auth, ceiling))
            .sessionManagement(sessionManagement -> sessionManagement
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .sessionAuthenticationStrategy(tokenCookieSessionAuthenticationStrategy))
            .csrf(csrf -> csrf
                    .csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    // Справочники здесь не перечислены: их изменения идут только с CSRF-токеном,
                    // фронтенд добавляет X-XSRF-TOKEN ко всем не-GET запросам (AxiosService)
                    .ignoringRequestMatchers(matchers(PublicEndpoints.csrfIgnored(ceiling)))
                    .sessionAuthenticationStrategy((authentication, request, response) -> {
                    }))
            // Роль SECURITY только читает: после правил доступа и CSRF, отказ — 403 через ExceptionTranslationFilter
            .addFilterAfter(new SecurityRoleReadOnlyFilter(), AuthorizationFilter.class);

        http.with(tokenCookieAuthenticationConfigurer, Customizer.withDefaults());

        return http.build();
    }

    /** Правила доступа по потолку; порядок — в описании класса */
    private static void authorize(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth,
            FeatureSnapshot ceiling) {
        auth.requestMatchers(matchers(PublicEndpoints.always())).permitAll();

        List<PublicEndpoints.Endpoint> machine = PublicEndpoints.machine(ceiling);
        if (!machine.isEmpty()) auth.requestMatchers(matchers(machine)).permitAll();

        // Роли функций из потолка — выше режима табло: функция с ролями без входа недоступна
        List<PublicEndpoints.RoleRule> roleRules = PublicEndpoints.roleRules(ceiling);
        if (!roleRules.isEmpty()) {
            auth.requestMatchers(new OrRequestMatcher(roleRules.stream().map(PublicEndpoints.RoleRule::matcher).toList()))
                    .access(featureRoles(roleRules, ceiling));
        }

        // 3D-модели, план цеха, ГПП — только после входа, даже в режиме табло (выше GET справочников)
        auth.requestMatchers(matchers(PublicEndpoints.signedIn())).authenticated();

        List<PublicEndpoints.Endpoint> anonymous = PublicEndpoints.anonymous(ceiling);
        if (!anonymous.isEmpty()) auth.requestMatchers(matchers(anonymous)).permitAll();

        // Справочники изменяются только после входа и с CSRF-токеном. Раньше их POST/PATCH/DELETE были открыты всем:
        // например, DELETE /api/nomenclature/items без входа удалял номенклатуру вместе с файлами 3D-моделей
        auth.requestMatchers(PublicEndpoints.REFERENCE_BOOKS.stream()
                        .map(AntPathRequestMatcher::antMatcher).toArray(RequestMatcher[]::new))
                .authenticated();
        auth.anyRequest().authenticated();
    }

    /**
     * Проверка ролей функций: запрос пропускается, только если роли пользователя подходят ко всем совпавшим
     * правилам — функции, её родителей и requires ({@link PublicEndpoints#rolesAllow}). Аноним не проходит
     */
    private static AuthorizationManager<RequestAuthorizationContext> featureRoles(
            List<PublicEndpoints.RoleRule> rules, FeatureSnapshot ceiling) {
        return (Supplier<Authentication> authentication, RequestAuthorizationContext context) -> {
            Authentication user = authentication.get();
            for (PublicEndpoints.RoleRule rule : rules) {
                if (rule.matcher().matches(context.getRequest())
                        && !PublicEndpoints.rolesAllow(ceiling, rule.feature(), user)) {
                    return new AuthorizationDecision(false);
                }
            }
            // Совпавшие правила пропустили: у каждой функции с ролями роль подошла, значит, пользователь вошёл
            return new AuthorizationDecision(user != null && user.isAuthenticated()
                    && !(user instanceof AnonymousAuthenticationToken));
        };
    }

    private static RequestMatcher[] matchers(List<PublicEndpoints.Endpoint> endpoints) {
        return endpoints.stream().map(PublicEndpoints.Endpoint::matcher).toArray(RequestMatcher[]::new);
    }

    /**
     * CORS: адреса фронтенда — из потолка ({@code awms.cors.allowed-origins}, раздел 4.5), а не из кода. Пустой список —
     * только тот же источник. Только точные адреса ({@link #corsOrigins}): ответы идут с учётными данными. Заголовок
     * ревизии профиля {@code X-AWMS-Features} открыт для чтения: иначе при кросс-доменном API (public/config.js) фронт
     * его не видит и не узнаёт о смене профиля. Этот же бин берут ворота функций для своих ответов 404
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsOrigins(
                FeatureResolver.resolve(environment).listParam(CeilingParam.CORS_ALLOWED_ORIGINS)));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-XSRF-TOKEN", "XSRF-TOKEN", "JSESSIONID", accessTokenCookieName, refreshTokenCookieName));
        configuration.setExposedHeaders(List.of(FeatureGateFilter.REVISION_HEADER));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Проверка {@code awms.cors.allowed-origins}: каждый адрес — точный источник страницы со схемой http или https,
     * хостом и, если нужно, портом, как в адресной строке. Подстановок нет: ответы CORS идут с учётными данными
     * (cookie), и «*» или {@code https://*} открыли бы API сайтам с любого адреса через браузер вошедшего
     * пользователя, включая CSRF-токен из {@code GET /csrf}. Spring запрещает «*» вместе с учётными данными только
     * в allowedOrigins, а через allowedOriginPatterns пропустил бы. Шаблоны с доменом ({@code https://*.zavod.local})
     * и списки портов ({@code :[8080,8081]}) не поддерживаются: проекту они не нужны, а список режется по запятым.
     * Источник {@code null} присылают страницы в песочнице и локальные файлы, его тоже нельзя. Ошибка отменяет запуск
     * и называет ключ. Открыт для проверки потолка без запуска службы (CLI, отчёт)
     */
    public static List<String> corsOrigins(List<String> configured) {
        List<String> origins = new ArrayList<>(configured.size());
        for (String origin : configured) {
            String problem = corsOriginProblem(origin);
            if (problem != null) {
                throw new FeatureConfigurationException(CeilingParam.CORS_ALLOWED_ORIGINS.key() + ": «" + origin
                        + "» — " + problem + ". Нужны точные адреса страниц через запятую, как в адресной строке "
                        + "браузера: https://awms.zavod.local,http://10.0.0.5:3000");
            }
            origins.add(origin);
        }
        return List.copyOf(origins);
    }

    /** Что не так с адресом CORS; null — точный источник http(s)://хост[:порт] */
    private static String corsOriginProblem(String origin) {
        if (origin.contains("*")) {
            return "подстановки («*», шаблоны адресов) не допускаются: ответы идут с учётными данными, API стало бы "
                    + "доступно сайтам с любого адреса";
        }
        if ("null".equalsIgnoreCase(origin)) {
            return "источник null (страницы в песочнице, локальные файлы) не допускается";
        }
        URI uri;
        try {
            uri = new URI(origin);
        } catch (URISyntaxException e) {
            return "это не адрес";
        }
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return "нужна схема http или https";
        }
        if (uri.getHost() == null || uri.getRawUserInfo() != null) {
            return "нужен адрес вида схема://хост или схема://хост:порт";
        }
        String path = uri.getRawPath();
        if ((path != null && !path.isEmpty() && !"/".equals(path)) || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            return "источник — это только схема, хост и порт, без пути и параметров";
        }
        return null;
    }

    @Bean
    public TokenCookieSessionAuthenticationStrategy tokenCookieSessionAuthenticationStrategy(TokenCookieJweStringSerializer tokenCookieJweStringSerializer) {
        var tokenCookieStrategy = new TokenCookieSessionAuthenticationStrategy(accessTokenCookieName, refreshTokenCookieName, accessTokenDuration, refreshTokenDuration, ip_address);
        tokenCookieStrategy.setTokenStringSerializer(tokenCookieJweStringSerializer);
        return tokenCookieStrategy;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
