package com.gov.landcheck.core.config.mongo;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;

/**
 * @Author: DangKong
 * @Date: 2023/3/1 23:19
 * @Description: mongodb 配置（含连接池优化）
 */

@Configuration
@Component
@EnableTransactionManagement
public class MongoDBConfig {

    /**
     * 获取配置文件中数据库信息
     */
    @Value("${spring.data.mongodb.database}")
    String db;

    // ---------- 连接池优化（可选，对应 application 中 app.mongodb.pool）----------
    @Value("${spring.data.mongodb.pool.max-size:100}")
    private int poolMaxSize;
    @Value("${spring.data.mongodb.pool.min-size:10}")
    private int poolMinSize;
    @Value("${spring.data.mongodb.pool.max-idle-time-ms:60000}")
    private long poolMaxIdleTimeMs;
    @Value("${spring.data.mongodb.pool.max-connection-life-time-ms:0}")
    private long poolMaxConnectionLifeTimeMs;
    @Value("${spring.data.mongodb.pool.max-wait-time-ms:5000}")
    private long poolMaxWaitTimeMs;

    /**
     * 连接池优化：对 MongoClient 应用连接池参数（最大/最小连接数、空闲与存活时间、获取连接等待时间）。
     * 配置项来自 application 中 app.mongodb.pool，未配置时使用上述默认值。
     */
    @Bean
    MongoClientSettingsBuilderCustomizer mongoClientSettingsBuilderCustomizer() {
        return builder -> builder.applyToConnectionPoolSettings(pool -> {
            pool.maxSize(poolMaxSize)
                    .minSize(poolMinSize)
                    .maxWaitTime(poolMaxWaitTimeMs, TimeUnit.MILLISECONDS)
                    .maxConnectionIdleTime(poolMaxIdleTimeMs, TimeUnit.MILLISECONDS);
            if (poolMaxConnectionLifeTimeMs > 0) {
                pool.maxConnectionLifeTime(poolMaxConnectionLifeTimeMs, TimeUnit.MILLISECONDS);
            }
        });
    }

    /**
     * GridFSBucket用于打开下载流
     *
     * @param mongoClient MongoClient
     * @return GridFSBucket
     */
    /**
     * GridFSBucket 由本配置提供；
     */
    @Bean
    GridFSBucket getGridFsBucket(MongoClient mongoClient) {
        MongoDatabase mongoDatabase = mongoClient.getDatabase(db); // 获取数据库
        GridFSBucket bucket = GridFSBuckets.create(mongoDatabase);
        return bucket;
    }

    /**
     * MongoDB 事务管理器。多文档事务要求副本集或 mongos；独立节点会在
     * {@link MongoReplicaSetRequirement} 启动校验时失败。
     */
    @Bean
    PlatformTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }

    // MongoDatabaseFactory 与 MongoTemplate 由 Spring Boot
    // * MongoDataAutoConfiguration 根据自动创建的 MongoClient 提供，此处不再重复定义，
    // * 否则会触发 MongoAutoConfiguration 的
    // @ConditionalOnMissingBean(MongoDatabaseFactory)
    // * 导致整个 Mongo 自动配置被跳过、MongoClient 未被创建。
}
