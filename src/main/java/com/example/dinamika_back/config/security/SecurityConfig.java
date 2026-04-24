package com.example.dinamika_back.config.security;

import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.example.dinamika_back.service.AuthService;
import com.example.dinamika_back.service.RefreshTokenService;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private AuthService authService;

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

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .addFilterAfter(getCsrfTokenFilter, ExceptionTranslationFilter.class)
            .authorizeHttpRequests(authorizeHttpRequests ->
                    authorizeHttpRequests
                            .requestMatchers("/error", "/api/auth/*", "/logout", "/api/auth/check_password", "/csrf").permitAll()
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
                            .anyRequest().authenticated())
            .sessionManagement(sessionManagement -> sessionManagement
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .sessionAuthenticationStrategy(tokenCookieSessionAuthenticationStrategy))
            .csrf(csrf -> csrf
                    .csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .ignoringRequestMatchers("/api/auth/login", "/api/auth/refresh_token", "/csrf", "/ws-stations/**")
                    .sessionAuthenticationStrategy((authentication, request, response) -> {
                    }));

        http.with(tokenCookieAuthenticationConfigurer, Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
    "http://localhost:3000",
    "http://127.0.0.1:3000",
    "http://109.69.22.155:3000",
    "http://109.69.22.155:8084",
    "http://localhost",
    "http://dynamikaawms.ru",
    "https://dynamikaawms.ru"
));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-XSRF-TOKEN", "XSRF-TOKEN", "JSESSIONID", accessTokenCookieName, refreshTokenCookieName));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
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