package com.example.dinamika_back.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import com.example.dinamika_back.model.Token;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Function;

public class AccessTokenCookieFactory implements Function<Authentication, Token> {


    private Duration tokenDuration;

    public AccessTokenCookieFactory(String duration) {
        this.tokenDuration = Duration.ofMinutes(Long.parseLong(duration));
    }

    @Override
    public Token apply(Authentication authentication) {
        var now = Instant.now();
        return new Token(UUID.randomUUID(), authentication.getName(),
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).toList(),
                now, now.plus(this.tokenDuration));
    }

    public void setTokenTtl(Duration tokenDuration) {
        this.tokenDuration = tokenDuration;
    }

}
