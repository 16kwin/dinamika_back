// SecurityConfig.java — ПОЛНЫЙ ФАЙЛ (добавлены /api/employees/**)
// Правила — как в main, с добавлениями поверх: 3D, план цеха, ГПП, справочники только на чтение без входа,
// флаги функций до входа (/api/features/public). Адреса CORS — прежние; файл потолка может только добавить свои.
// Поверх правил — слой потолка (CeilingAccessFilter, пакет B5): роли функций, выключение анонимного доступа,
// сети станций и табло, роль SECURITY только читает. Он только сужает; без файла потолка и при full не меняет ничего
package com.example.dinamika_back.config.security;

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
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.example.dinamika_back.features.CeilingParam;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.FeatureConfigurationException;
import com.example.dinamika_back.features.FeatureGateFilter;
import com.example.dinamika_back.features.FeatureResolver;
import com.example.dinamika_back.features.FeatureSnapshot;
import com.example.dinamika_back.service.AuthService;
import com.example.dinamika_back.service.RefreshTokenService;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("AWMS.SECURITY");

    /** Справочники, открытые на чтение без входа: изменять их можно только после входа и с CSRF-токеном */
    private static final String[] REFERENCE_BOOKS = {
            "/api/nomenclature/**",
            "/api/templates/**",
            "/api/enterprises/**",
            "/api/workshops/**",
            "/api/sections/**",
            "/api/employees/**",
            "/api/station-types/**",
            "/api/station-manufacturers/**",
            "/api/station-models/**",
            "/api/station-configurations/**",
            "/api/stations/crud/**"
    };

    private AuthService authService;

    @Autowired
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
        CeilingAccessFilter ceilingAccess = new CeilingAccessFilter(ceiling);
        logRules(ceiling, ceilingAccess);
        // Без проверки CSRF-токена — прежний список, суженный по функциям потолка (не шире прежнего).
        // Пути без метода — строками, как раньше; колбэки заказов из реестра — с методом (только без режима табло)
        List<PublicEndpoints.Endpoint> csrfIgnored = PublicEndpoints.csrfIgnored(ceiling);
        String[] csrfIgnoredPatterns = csrfIgnored.stream().filter(e -> e.method() == null)
                .map(PublicEndpoints.Endpoint::pattern).toArray(String[]::new);
        RequestMatcher[] csrfIgnoredCalls = csrfIgnored.stream().filter(e -> e.method() != null)
                .map(PublicEndpoints.Endpoint::matcher).toArray(RequestMatcher[]::new);

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .addFilterAfter(getCsrfTokenFilter, ExceptionTranslationFilter.class)
            .authorizeHttpRequests(authorizeHttpRequests ->
                    authorizeHttpRequests
                            .requestMatchers("/error", "/api/auth/*", "/logout", "/api/auth/check_password", "/csrf").permitAll()
                            // Флаги функций до входа (pwa, passwords): страница входа решает, предлагать ли установку и сохранение пароля
                            .requestMatchers(HttpMethod.GET, "/api/features/public").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/locations/hierarchy").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/locations/hierarchy/first").permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/stations/static/filtered").permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/stations/dynamic/filtered").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/locations/*/photo").permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/locations/*/photo").authenticated()
                            .requestMatchers(HttpMethod.DELETE, "/api/locations/*/photo").authenticated()
                            .requestMatchers("/uploads/**").permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/locations").authenticated()
                            .requestMatchers(HttpMethod.GET, "/api/stations/static").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/stations/static/**").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/stations/dynamic").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/stations/dynamic/**").permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/stations").permitAll()
                            .requestMatchers("/ws-stations/**").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/user/filters").authenticated()
                            .requestMatchers(HttpMethod.POST, "/api/user/filters").authenticated()
                            .requestMatchers(HttpMethod.DELETE, "/api/user/filters").authenticated()
                            .requestMatchers(HttpMethod.POST,"/api/animal_card/**").hasAnyRole("ADMIN", "OPERATOR")
                            .requestMatchers(HttpMethod.PATCH,"/api/animal_card/**").hasAnyRole("ADMIN", "OPERATOR")
                            .requestMatchers(HttpMethod.DELETE,"/api/animal_card/**").hasAnyRole("ADMIN", "OPERATOR")
                            .requestMatchers(HttpMethod.POST,"/api/docs/**").hasAnyRole("ADMIN", "OPERATOR")
                            .requestMatchers(HttpMethod.PATCH,"/api/docs/**").hasAnyRole("ADMIN", "OPERATOR")
                            .requestMatchers(HttpMethod.DELETE,"/api/docs/**").hasAnyRole("ADMIN", "OPERATOR")
                            .requestMatchers(HttpMethod.POST,"/api/information/**").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.PATCH,"/api/information/**").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.DELETE,"/api/information/**").hasRole("ADMIN")
                            .requestMatchers("/api/notification/**").hasAnyRole("ADMIN", "OPERATOR")
                            .requestMatchers("/api/users/**").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.POST, "/api/test-documents/**").authenticated()
                            .requestMatchers(HttpMethod.PUT, "/api/test-documents/**").authenticated()
                            .requestMatchers(HttpMethod.GET, "/api/test-documents/**").authenticated()
                            .requestMatchers(HttpMethod.DELETE, "/api/test-documents/**").authenticated()
                            .requestMatchers(HttpMethod.POST, "/api/sync/**").permitAll()
                            // 3D-модели номенклатуры — файлы конструкторской документации: только после входа.
                            // Стоит выше общего правила номенклатуры, иначе его перехватит permitAll.
                            // /api/nomenclature/models (без /3d) — справочник моделей брендов, он остаётся открытым
                            .requestMatchers("/api/nomenclature/*/models", "/api/nomenclature/models/3d/**").authenticated()
                            // План цеха и расположение станций: смотреть — после входа, править — руководителю (проверка в контроллере)
                            .requestMatchers("/api/station-plans/**").authenticated()
                            // График подготовки производства: смотреть — после входа, сохранять — руководителю (проверка в контроллере)
                            .requestMatchers("/api/gpp/**").authenticated()
                            // Справочники: читать (GET) можно без входа, изменять — только после входа и с CSRF-токеном.
                            // Раньше их POST/PATCH/DELETE были открыты всем: например, DELETE /api/nomenclature/items
                            // без входа удалял номенклатуру вместе с файлами 3D-моделей
                            .requestMatchers(HttpMethod.GET, REFERENCE_BOOKS).permitAll()
                            .requestMatchers(REFERENCE_BOOKS).authenticated()
                            // Заказы и ТКП принимают изменения от SAAS (через Задел) без входа — правила прежние
                            .requestMatchers("/api/orders/**").permitAll()
                            .requestMatchers("/api/tkp/**").permitAll()
                            .requestMatchers("/api/dashboard/**").permitAll()
                            .anyRequest().authenticated())
            .sessionManagement(sessionManagement -> sessionManagement
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .sessionAuthenticationStrategy(tokenCookieSessionAuthenticationStrategy))
            .csrf(csrf -> {
                csrf
                    .csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    // Было: "/api/auth/login", "/api/auth/refresh_token", "/csrf", "/ws-stations/**", "/api/sync/**",
                    // "/api/orders/**", "/api/tkp/**", "/api/dashboard/**". Теперь тот же список по функциям потолка
                    // (PublicEndpoints.csrfIgnored): при full — тот же, /api/sync/** закрыт воротами (testsync выключен).
                    // Справочники (REFERENCE_BOOKS) здесь не перечислены: их изменения идут только с CSRF-токеном,
                    // фронтенд добавляет X-XSRF-TOKEN ко всем не-GET запросам (AxiosService)
                    .ignoringRequestMatchers(csrfIgnoredPatterns)
                    .sessionAuthenticationStrategy((authentication, request, response) -> {
                    });
                if (csrfIgnoredCalls.length > 0) csrf.ignoringRequestMatchers(csrfIgnoredCalls);
            })
            // Слой потолка — после прежних правил: сюда доходят только пропущенные ими запросы, слой может только
            // отказать (аноним — 401, вошедший — 403). Роль SECURITY вне профиля безопасности только читает (403)
            .addFilterAfter(ceilingAccess, AuthorizationFilter.class)
            .addFilterAfter(new SecurityRoleReadOnlyFilter(), CeilingAccessFilter.class)
            // С телефона и планшета вошедший пользователь только смотрит (awms.mobile.read-only, по умолчанию да)
            .addFilterAfter(new MobileReadOnlyFilter(ceiling.boolParam(CeilingParam.MOBILE_READ_ONLY)),
                    SecurityRoleReadOnlyFilter.class);

        http.with(tokenCookieAuthenticationConfigurer, Customizer.withDefaults());

        return http.build();
    }

    /** Одна строка в лог AWMS.SECURITY при старте: что слой потолка сужает на этой установке */
    private static void logRules(FeatureSnapshot ceiling, CeilingAccessFilter ceilingAccess) {
        List<String> networks = ceiling.listParam(CeilingParam.ANONYMOUS_ALLOWED_NETWORKS);
        SECURITY_LOG.info("Правила доступа: прежние правила SecurityConfig{}; анонимный доступ (режим табло) {}{}; "
                        + "роли функций из потолка — {} правил(а); доверенные прокси: {}; роль SECURITY вне профиля "
                        + "безопасности только читает",
                ceilingAccess.narrows() ? " и слой потолка" : " без изменений",
                ceiling.isOn(Feature.ANONYMOUS) ? "включён" : "выключен",
                networks.isEmpty() ? (ceiling.isOn(Feature.ANONYMOUS) ? ", с любых адресов" : "")
                        : ", только с адресов " + String.join(", ", networks),
                PublicEndpoints.roleRules(ceiling).size(),
                ceiling.listParam(CeilingParam.TRUSTED_PROXIES).isEmpty() ? "нет"
                        : String.join(", ", ceiling.listParam(CeilingParam.TRUSTED_PROXIES)));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = new ArrayList<>(Arrays.asList(
    "http://localhost:3000",
    "http://127.0.0.1:3000",
    "http://109.69.22.155:3000",
    "http://109.69.22.155:8084",
    "http://localhost",
    "http://dynamikaawms.ru",
    "https://dynamikaawms.ru"
));
        // Поверх прежнего списка — адреса из файла потолка (awms.cors.allowed-origins), если они там заданы
        for (String extra : corsOrigins(FeatureResolver.resolve(environment).listParam(CeilingParam.CORS_ALLOWED_ORIGINS))) {
            if (!origins.contains(extra)) origins.add(extra);
        }
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-XSRF-TOKEN", "XSRF-TOKEN", "JSESSIONID", accessTokenCookieName, refreshTokenCookieName));
        // Ревизия профиля функций: фронтенд читает этот заголовок и при кросс-доменном API
        configuration.setExposedHeaders(List.of(FeatureGateFilter.REVISION_HEADER, MobileReadOnlyFilter.READ_ONLY_HEADER));
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