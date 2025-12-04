package com.example.dinamika_back.config.security;


import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import com.example.dinamika_back.model.Token;
import com.example.dinamika_back.model.TokenUser;

import java.time.Instant;

public class TokenAuthenticationUserDetailsService implements AuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken> {


    @Override
    public UserDetails loadUserDetails(PreAuthenticatedAuthenticationToken authenticationToken) throws UsernameNotFoundException {
        if (authenticationToken.getPrincipal() instanceof Token token) {
            return new TokenUser(token.subject(), "nopassword", true, true,
                    token.expiresTime().isAfter(Instant.now()), true, token.authorities().stream().map(SimpleGrantedAuthority::new).toList(), token);
        }

        throw new UsernameNotFoundException("Principal is not a Token");
    }
}
