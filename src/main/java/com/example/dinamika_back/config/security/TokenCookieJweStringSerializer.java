package com.example.dinamika_back.config.security;

import com.nimbusds.jose.*;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.dinamika_back.model.Token;

import java.util.Date;
import java.util.function.Function;

public class TokenCookieJweStringSerializer implements Function<Token, String> {

    private static final Logger logger = LoggerFactory.getLogger(TokenCookieJweStringSerializer.class);

    private final JWEEncrypter jweEncrypter;

    private JWEAlgorithm jweAlgorithm = JWEAlgorithm.DIR;

    private EncryptionMethod encryptionMethod = EncryptionMethod.A128GCM;

    public TokenCookieJweStringSerializer(JWEEncrypter jweEncrypter) {
        this.jweEncrypter = jweEncrypter;
    }

    public TokenCookieJweStringSerializer(JWEEncrypter jweEncrypter, JWEAlgorithm jweAlgorithm, EncryptionMethod encryptionMethod) {
        this.jweEncrypter = jweEncrypter;
        this.jweAlgorithm = jweAlgorithm;
        this.encryptionMethod = encryptionMethod;
    }

    @Override
    public String apply(Token token) {
        var jwsHeader = new JWEHeader.Builder(this.jweAlgorithm, this.encryptionMethod)
                .keyID(token.id().toString())
                .build();
        var claimsSet = new JWTClaimsSet.Builder()
                .jwtID(token.id().toString())
                .subject(token.subject())
                .issueTime(Date.from(token.createTime()))
                .expirationTime(Date.from(token.expiresTime()))
                .claim("authorities", token.authorities())
                .build();
        var encryptedJWT = new EncryptedJWT(jwsHeader, claimsSet);
        try {


            encryptedJWT.encrypt(this.jweEncrypter);


            return encryptedJWT.serialize();
        } catch (JOSEException exception) {
            logger.error("Не удалось поработать с jwt токеном (TokenCookieJweStringSerializer) {}", exception.getMessage());
        }

        return null;
    }

    public void setJweAlgorithm(JWEAlgorithm jweAlgorithm) {
        this.jweAlgorithm = jweAlgorithm;
    }

    public void setEncryptionMethod(EncryptionMethod encryptionMethod) {
        this.encryptionMethod = encryptionMethod;
    }
}
