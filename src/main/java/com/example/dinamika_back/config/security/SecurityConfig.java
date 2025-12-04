package com.example.dinamika_back.config.security;

import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.example.dinamika_back.service.AuthService;
import com.example.dinamika_back.service.RefreshTokenService;
import com.example.dinamika_back.service.UserService;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private AuthService authService;

    // Имена куков и длительность
    private @Value("${jwt.access-token-name}") String accessTokenCookieName;
    private @Value("${jwt.refresh-token-name}") String refreshTokenCookieName;

    private @Value("${jwt.access-token-expiration-minutes}") String accessTokenDuration;
    private @Value("${jwt.refresh-token-expiration-minutes}") String refreshTokenDuration;

    // Фронтенд
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
            TokenCookieSessionAuthenticationStrategy tokenCookieSessionAuthenticationStrategy) throws Exception {

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Настройка CORS
            .addFilterAfter(new GetCsrfTokenFilter(), ExceptionTranslationFilter.class)
            .authorizeHttpRequests(authorizeHttpRequests ->
                    authorizeHttpRequests
                            .requestMatchers("/error", "/api/auth/*", "/logout").permitAll()
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

                            .anyRequest().authenticated())
            .sessionManagement(sessionManagement -> sessionManagement
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .sessionAuthenticationStrategy(tokenCookieSessionAuthenticationStrategy))

            //.csrf(csrf -> csrf.disable());
            .csrf(csrf -> csrf.csrfTokenRepository(new CookieCsrfTokenRepository())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .sessionAuthenticationStrategy((authentication, request, response) -> {
                    }));

        http.with(tokenCookieAuthenticationConfigurer, Customizer.withDefaults());


        return http.build();
    }



    // CORS защита
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(ip_address + ":3000"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-XSRF-TOKEN", "XSRF-TOKEN", "JSESSIONID", accessTokenCookieName, refreshTokenCookieName));
        configuration.setAllowCredentials(true); // Разрешить cookies
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

    //
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }


    @Bean
    public UserDetailsService userDetailsService() {
        return new AuthService();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }



}
