package com.example.dinamika_back.config.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import com.example.dinamika_back.model.Token;
import com.example.dinamika_back.service.RefreshTokenService;
import com.example.dinamika_back.service.UserService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.Function;

public class TokenCookieSessionAuthenticationStrategy implements SessionAuthenticationStrategy {

    // Имена куков и длительность
    private String accessTokenCookieName;
    private String refreshTokenCookieName;
    private String accessTokenDuration;
    private String refreshTokenDuration;

    // Фронтенд
    private String ip_address;

    private Function<Authentication, Token> accessTokenCookieFactory;
    private Function<Authentication, Token> refreshTokenCookieFactory;

    private RefreshTokenService refreshTokenService;

    public TokenCookieSessionAuthenticationStrategy(String accessTokenCookieName, String refreshTokenCookieName, String accessTokenDuration, String refreshTokenDuration, String ip_address) {
        this.accessTokenCookieName = accessTokenCookieName;
        this.refreshTokenCookieName = refreshTokenCookieName;
        this.accessTokenDuration = accessTokenDuration;
        this.accessTokenCookieFactory = new AccessTokenCookieFactory(accessTokenDuration);
        this.refreshTokenDuration = refreshTokenDuration;
        this.refreshTokenCookieFactory = new RefreshTokenCookieFactory(refreshTokenDuration);
        this.ip_address = ip_address;

    }

    @Autowired
    public void setRefreshTokenService(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    private Function<Token, String> tokenStringSerializer = Objects::toString;

    @Override
    public void onAuthentication(Authentication authentication, HttpServletRequest request,
                                 HttpServletResponse response) throws SessionAuthenticationException {
        if (authentication instanceof UsernamePasswordAuthenticationToken) {

            // Access Token
            Token accessToken = accessTokenCookieFactory.apply(authentication);
            String accessTokenString = tokenStringSerializer.apply(accessToken);

            var accessCookie = new Cookie(accessTokenCookieName, accessTokenString);

            accessCookie.setPath("/");
            accessCookie.setDomain(null);
            accessCookie.setSecure(false);
            accessCookie.setHttpOnly(true);
            accessCookie.setMaxAge((int) ChronoUnit.SECONDS.between(Instant.now().atZone(ZoneId.systemDefault()), accessToken.expiresTime().atZone(ZoneId.systemDefault())));

            response.addCookie(accessCookie);



            // Refresh Token
            Token refreshToken = refreshTokenCookieFactory.apply(authentication);
            String refreshTokenString = tokenStringSerializer.apply(refreshToken);


            // Удаляем все старые рефреш токены данной учетки
            refreshTokenService.deleteTokensByUser(authentication.getName());

            // Сохраняем токен в БД

            refreshTokenService.saveToken(
                    refreshTokenString,
                    authentication.getName(),
                    LocalDateTime.ofInstant(refreshToken.expiresTime(), ZoneId.systemDefault()),
                    true
            );

            Cookie refreshCookie = new Cookie(refreshTokenCookieName, refreshTokenString);
            refreshCookie.setHttpOnly(true);
            refreshCookie.setPath("/");
            refreshCookie.setMaxAge((int) ChronoUnit.SECONDS.between(Instant.now(), refreshToken.expiresTime()));
            response.addCookie(refreshCookie);
        }
    }

    public void setAccessTokenCookieFactory(Function<Authentication, Token> accessTokenCookieFactory) {
        this.accessTokenCookieFactory = accessTokenCookieFactory;
    }

    public void setRefreshTokenCookieFactory(Function<Authentication, Token> refreshTokenCookieFactory) {
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
    }

    public void setTokenStringSerializer(Function<Token, String> tokenStringSerializer) {
        this.tokenStringSerializer = tokenStringSerializer;
    }

    public void setAccessTokenCookieName(String accessTokenCookieName) {
        this.accessTokenCookieName = accessTokenCookieName;
    }

    public void setRefreshTokenCookieName(String refreshTokenCookieName) {
        this.refreshTokenCookieName = refreshTokenCookieName;
    }

    public void setAccessTokenDuration(String accessTokenDuration) {
        this.accessTokenDuration = accessTokenDuration;
    }

    public void setRefreshTokenDuration(String refreshTokenDuration) {
        this.refreshTokenDuration = refreshTokenDuration;
    }

    public void setIp_address(String ip_address) {
        this.ip_address = ip_address;
    }
}