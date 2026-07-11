package com.gov.landcheck.core.config.mq.serializer;

/**
 * 消息体序列化/反序列化统一接口，供生产端与消费端复用。
 *
 * @author landcheck
 */
public interface MessageSerializer {

    /**
     * 将对象序列化为字节数组
     *
     * @param object 待序列化对象
     * @return 字节数组，null 表示序列化失败
     */
    byte[] serialize(Object object);

    /**
     * 将字节数组反序列化为指定类型
     *
     * @param bytes 字节数组
     * @param clazz 目标类型
     * @param <T>   类型
     * @return 反序列化结果，null 表示反序列化失败
     */
    <T> T deserialize(byte[] bytes, Class<T> clazz);
}
