package com.gov.landcheck.core.config.cache.key;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class StableKeyGenerator {

    private final ObjectMapper objectMapper;
    private final KeyNormalizer keyNormalizer;

    /**
     * 使用默认（无 default typing）的 ObjectMapper 序列化规范化结果，保证缓存 key 与请求体 JSON 一致。
     */
    public StableKeyGenerator(ObjectMapper objectMapper, KeyNormalizer keyNormalizer) {
        this.objectMapper = objectMapper;
        this.keyNormalizer = keyNormalizer;
    }

    public String hashOf(Object payload) {
        try {
            Object normalized = keyNormalizer.normalize(payload);
            String canonical = objectMapper.writeValueAsString(normalized);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to generate stable cache key", ex);
        }
    }
}
