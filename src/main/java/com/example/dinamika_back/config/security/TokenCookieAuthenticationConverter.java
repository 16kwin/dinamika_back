package com.example.dinamika_back.config.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import com.example.dinamika_back.model.Token;

import java.util.function.Function;
import java.util.stream.Stream;

public class TokenCookieAuthenticationConverter implements AuthenticationConverter {

    private final Function<String, Token> tokenCookieStringDeserializer;

    // Имена куков
    private String accessTokenCookieName;

    public TokenCookieAuthenticationConverter(Function<String, Token> tokenCookieStringDeserializer, String accessTokenCookieName) {
        this.tokenCookieStringDeserializer = tokenCookieStringDeserializer;
        this.accessTokenCookieName = accessTokenCookieName;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (request.getCookies() != null) {
//            Cookie[] cookies = request.getCookies();
//            for (Cookie cookie : cookies) {
//                System.out.println(cookie.getName());
//            }
            return Stream.of(request.getCookies())
                    .filter(cookie -> cookie.getName().equals(accessTokenCookieName))
                    .findFirst()
                    .map(cookie -> {
                        //System.out.println("found cookie: " + cookie.getValue());
                        var token = this.tokenCookieStringDeserializer.apply(cookie.getValue());
                        return new PreAuthenticatedAuthenticationToken(token, cookie.getValue());
                    })
                    .orElse(null);

        }

        return null;
    }
}