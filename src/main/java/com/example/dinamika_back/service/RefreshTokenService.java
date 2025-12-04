package com.example.dinamika_back.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.example.dinamika_back.config.security.TokenCookieJweStringDeserializer;
import com.example.dinamika_back.model.RefreshTokenDbEntity;
import com.example.dinamika_back.model.Token;
import com.example.dinamika_back.model.User;
import com.example.dinamika_back.repository.RefreshTokenRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserService userService;
    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserService userService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userService = userService;
    }

    public Optional<RefreshTokenDbEntity> findByToken(String tokenStr) {
        return refreshTokenRepository.findByToken(tokenStr);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveToken(String token, String username, LocalDateTime expireTime, boolean isActual) {

        Optional<User> user = userService.getUserByUsername(username);
        if (!user.isPresent()) {
            logger.error("При попытке сохранить RefreshToken в БД пользователь с ником {} не найден", username);
        }
        else {
            RefreshTokenDbEntity refreshTokenDbEntity = new RefreshTokenDbEntity(token, user.get(), LocalDateTime.now(ZoneId.systemDefault()), expireTime, isActual);
            refreshTokenRepository.save(refreshTokenDbEntity);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteToken(String token) {
        refreshTokenRepository.deleteByToken(token);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteTokensByUser(String username) {
        Optional<User> user = userService.getUserByUsername(username);
        if (!user.isPresent()) {
            logger.error("При попытке удалить рефреш токены в БД пользователь с ником {} не найден", username);
        }
        refreshTokenRepository.deleteByUserId(user.get().getId());
    }

}
