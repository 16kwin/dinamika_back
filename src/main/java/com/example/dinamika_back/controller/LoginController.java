package com.example.dinamika_back.controller;

import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.example.dinamika_back.config.security.*;
import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.*;
import com.example.dinamika_back.service.*;
import com.example.dinamika_back.service.RefreshTokenService;
import com.example.dinamika_back.service.UserService;


import javax.swing.text.html.Option;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

@RestController
@RequestMapping("/api/auth")
public class LoginController {

    // Имена куков и длительность
    private @Value("${jwt.access-token-name}") String accessTokenCookieName;
    private @Value("${jwt.access-token-expiration-minutes}") String accessTokenDuration;

    private @Value("${jwt.refresh-token-name}") String refreshTokenCookieName;
    private @Value("${jwt.refresh-token-expiration-minutes}") String refreshTokenDuration;

    private @Value("${jwt.cookie-token-key}") String cookieTokenKey;


    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);
    private final AuthenticationManager authenticationManager;
    private final TokenCookieSessionAuthenticationStrategy tokenCookieSessionAuthenticationStrategy;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private Function<String, Token> tokenDeserializer;
    private Function<Token, String> tokenSerializer;

    private Function<Authentication, Token> accessTokenCookieFactory;
    private Function<Authentication, Token> refreshTokenCookieFactory;

    @Autowired
    public LoginController(AuthenticationManager authenticationManager,
                           TokenCookieSessionAuthenticationStrategy tokenCookieSessionAuthenticationStrategy,
                           UserService userService,
                           RefreshTokenService refreshTokenService,
                           TokenCookieJweStringSerializer jweStringSerializer) throws ParseException, KeyLengthException {
        this.authenticationManager = authenticationManager;
        this.tokenCookieSessionAuthenticationStrategy = tokenCookieSessionAuthenticationStrategy;
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.tokenSerializer = jweStringSerializer;
    }


    @PostMapping("/login")
    public ResponseEntity<?> authenticate(@RequestBody LoginDTO loginDTO, HttpServletRequest request, HttpServletResponse response) {

        try {
            // Создание аутентификации
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginDTO.getUsername(),
                            loginDTO.getPassword()
                    )
            );

            // Установка аутентификации в контекст
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Вызываем стратегию для установки токена в cookie
            tokenCookieSessionAuthenticationStrategy.onAuthentication(authentication, request, response);

            return ResponseEntity.ok("Успешная аутентификация");
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }
    }

    @GetMapping("/refresh_token")
    public ResponseEntity<?> refreshToken(HttpServletRequest request, HttpServletResponse response) {

        try {
            // Извлекем куку рефреш токена
            String refreshTokenStr = extractCookie(request, refreshTokenCookieName)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token отсутствует"));

            // Достаем сам токен

            this.tokenDeserializer = new TokenCookieJweStringDeserializer(new DirectDecrypter(
                    OctetSequenceKey.parse(cookieTokenKey)
            ));

            Token oldRefreshToken;
            try {
                oldRefreshToken = tokenDeserializer.apply(refreshTokenStr);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token невалидный");
            }

            // Проверяем наличие в БД
            if (!refreshTokenService.findByToken(refreshTokenStr).isPresent()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token не найден в БД");
            }

            // Проверяем не истек ли
            if (Instant.now().getEpochSecond() > oldRefreshToken.expiresTime().getEpochSecond()) {
                refreshTokenService.deleteToken(refreshTokenStr);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token истек");
            }

            // Узнаем username и роли
            String username = oldRefreshToken.subject();
            List<? extends GrantedAuthority> authorities = oldRefreshToken.authorities().stream().map(SimpleGrantedAuthority::new).toList(); // если есть

            // Новый объект authentication
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    username, null, authorities);

            // Установка аутентификации в контекст
            // SecurityContextHolder.getContext().setAuthentication(authentication);

            // Генерируем новый Access токен
            accessTokenCookieFactory = new AccessTokenCookieFactory(accessTokenDuration);
            Token newAccessToken = accessTokenCookieFactory.apply(authentication);
            String newAccessTokenStr = tokenSerializer.apply(newAccessToken);

            // Генерируем новый Refresh токен
//            refreshTokenCookieFactory = new RefreshTokenCookieFactory(refreshTokenDuration);
//            Token newRefreshToken = refreshTokenCookieFactory.apply(authentication);
//            String newRefreshTokenStr = tokenSerializer.apply(newRefreshToken);


            // Удаление неактуальных рефреш токенов и добавление нового в БД проихсодит в классе TokenCookieSessionAuthenticationStrategy
//            // Сохраняем новый Refresh токен в БД и инвалидируем старый
//            refreshTokenService.saveToken(
//                    newRefreshTokenStr,
//                    authentication.getName(),
//                    LocalDateTime.ofInstant(newRefreshToken.expiresTime(), ZoneId.systemDefault()),
//                    false
//            );

            //refreshTokenService.deleteToken(refreshTokenStr);

            // Обновляем куки
            Cookie accessCookie = createHttpOnlySecureCookie(accessTokenCookieName, newAccessTokenStr, (int) ChronoUnit.SECONDS.between(Instant.now(), newAccessToken.expiresTime()));
            response.addCookie(accessCookie);

            //Cookie refreshCookie = createHttpOnlySecureCookie(refreshTokenCookieName, newRefreshTokenStr, (int) ChronoUnit.SECONDS.between(Instant.now().atZone(ZoneId.systemDefault()), newRefreshToken.expiresTime().atZone(ZoneId.systemDefault())));
            //response.addCookie(refreshCookie);

            // Отдаём Access токен
            //return ResponseEntity.ok(Map.of("newAccessToken", tokenSerializer.apply(newAccessToken)));

            AuthDTO authDTO = new AuthDTO();
            try {
                authDTO.setUsername(authentication.getName());

                Optional<User> user = userService.getUserByUsername(authentication.getName());
                if (user.isPresent()) {
                    authDTO.setRole(user.get().getRole().getName());
                    authDTO.setRoleDescription(user.get().getRole().getDescription());
                    authDTO.setFirstName(user.get().getFirstName());
                    authDTO.setMiddleName(user.get().getMiddleName());
                    authDTO.setLastName(user.get().getLastName());
                }

                return ResponseEntity.ok(authDTO);
            } catch (Exception e) {
                logger.error("Ошибка аутентификации при обновлении Refresh токена {}", e.getMessage());
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (Exception ex) {
            logger.error("Не удалось поработать с Refresh токеном: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
        }

    }

    private Optional<String> extractCookie(HttpServletRequest request, String name) {
        return Arrays.stream(Optional.ofNullable(request.getCookies()).orElse(new Cookie[0]))
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    private Cookie createHttpOnlySecureCookie(String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        return cookie;
    }

    // Периодическая проверка аутентификации
    @GetMapping("/check_auth")
    public ResponseEntity<?> checkAuth(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        AuthDTO authDTO = new AuthDTO();
        try {
            authDTO.setUsername(authentication.getName());

            Optional<User> user = userService.getUserByUsername(authentication.getName());
            if (user.isPresent()) {
                authDTO.setId(user.get().getId().toString());
                authDTO.setRole(user.get().getRole().getName());
                authDTO.setRoleDescription(user.get().getRole().getDescription());
                authDTO.setFirstName(user.get().getFirstName());
                authDTO.setMiddleName(user.get().getMiddleName());
                authDTO.setLastName(user.get().getLastName());
            }

            return ResponseEntity.ok(authDTO);
        } catch (Exception e) {
            logger.error("Ошибка аутентификации {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void setTokenDeserializer(Function<String, Token> tokenDeserializer) {
        this.tokenDeserializer = tokenDeserializer;
    }

    public void setTokenSerializer(Function<Token, String> tokenSerializer) {
        this.tokenSerializer = tokenSerializer;
    }

    public void setAccessTokenCookieFactory(Function<Authentication, Token> accessTokenCookieFactory) {
        this.accessTokenCookieFactory = accessTokenCookieFactory;
    }

    public void setRefreshTokenCookieFactory(Function<Authentication, Token> refreshTokenCookieFactory) {
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
    }


}


