package com.example.dinamika_back.config.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.csrf.CsrfFilter;
import com.example.dinamika_back.model.Token;
import com.example.dinamika_back.model.TokenUser;
import com.example.dinamika_back.service.RefreshTokenService;

import java.util.function.Function;

public class TokenCookieAuthenticationConfigurer
        extends AbstractHttpConfigurer<TokenCookieAuthenticationConfigurer, HttpSecurity> {

    private Function<String, Token> tokenCookieStringDeserializer;

    // Имена куков
    private String accessTokenCookieName;
    private String refreshTokenCookieName;

    private RefreshTokenService refreshTokenService;

    public TokenCookieAuthenticationConfigurer() {}

    public TokenCookieAuthenticationConfigurer(String accessTokenCookieName, String refreshTokenCookieName, RefreshTokenService refreshTokenService) {
        this.accessTokenCookieName = accessTokenCookieName;
        this.refreshTokenCookieName = refreshTokenCookieName;
        this.refreshTokenService = refreshTokenService;
    }

//    @Bean
//    public CustomLogoutSuccessHandler customLogoutSuccessHandler() {
//        return new CustomLogoutSuccessHandler();
//    }

    @Override
    public void init(HttpSecurity builder) throws Exception {
        builder.logout(logout -> logout
                .addLogoutHandler(
                        new CookieClearingLogoutHandler(accessTokenCookieName, refreshTokenCookieName))
                .addLogoutHandler((request, response, authentication) -> {
                    if (authentication != null &&
                            authentication.getPrincipal() instanceof TokenUser user) {
                        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    }
                })
                .logoutSuccessHandler(new CustomLogoutSuccessHandler(refreshTokenService))
        );
    }

    @Override
    public void configure(HttpSecurity builder) throws Exception {
        var cookieAuthenticationFilter = new AuthenticationFilter(
                builder.getSharedObject(AuthenticationManager.class),
                new TokenCookieAuthenticationConverter(this.tokenCookieStringDeserializer, accessTokenCookieName));
        cookieAuthenticationFilter.setSuccessHandler((request, response, authentication) -> {
        });
        cookieAuthenticationFilter.setFailureHandler(
                new AuthenticationEntryPointFailureHandler(
                        new Http403ForbiddenEntryPoint()
                )
        );

        var authenticationProvider = new PreAuthenticatedAuthenticationProvider();
        authenticationProvider.setPreAuthenticatedUserDetailsService(
                new TokenAuthenticationUserDetailsService());

        builder.addFilterAfter(cookieAuthenticationFilter, CsrfFilter.class)
                .authenticationProvider(authenticationProvider);
    }

    public TokenCookieAuthenticationConfigurer tokenCookieStringDeserializer(
            Function<String, Token> tokenCookieStringDeserializer) {
        this.tokenCookieStringDeserializer = tokenCookieStringDeserializer;
        return this;
    }

}