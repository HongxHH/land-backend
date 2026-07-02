package com.gov.landcheck.core.config.mq.serializer.impl;

import com.gov.landcheck.core.config.mq.serializer.MessageSerializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 基于 Jackson 的 JSON 消息序列化实现。
 * 若容器中无 ObjectMapper 则使用默认配置（含 JavaTimeModule）。
 *
 * @author landcheck
 */
@Slf4j
@Component
public class JsonMessageSerializer implements MessageSerializer {

    private final ObjectMapper objectMapper;

    @Autowired
    public JsonMessageSerializer(@Autowired(required = false) ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : createDefaultObjectMapper();
    }

    private static ObjectMapper createDefaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Override
    public byte[] serialize(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            log.warn("消息序列化失败: type={}", object.getClass().getName(), e);
            return null;
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(bytes, clazz);
        } catch (Exception e) {
            log.warn("消息反序列化失败: clazz={}", clazz.getName(), e);
            return null;
        }
    }
}
