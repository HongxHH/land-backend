package com.gov.landcheck.core.config.mongo;

import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.mongodb.client.MongoClient;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 启动时确认 Mongo 拓扑支持多文档事务（副本集或 mongos）。独立节点直接拒绝启动。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MongoReplicaSetRequirement implements ApplicationRunner {

    @Resource
    private MongoClient mongoClient;

    @Override
    public void run(ApplicationArguments args) {
        Document hello = mongoClient.getDatabase("admin").runCommand(new Document("hello", 1));
        if (!supportsMultiDocumentTransactions(hello)) {
            throw new IllegalStateException(
                    "MongoDB 必须是副本集或 mongos，独立节点不支持多文档事务。"
                            + " 请使用 replicaSet=... 连接串，并确认已 rs.initiate()。"
                            + " hello=" + hello.toJson());
        }
        log.info("MongoDB 事务拓扑已确认: {}", describeTopology(hello));
    }

    static boolean supportsMultiDocumentTransactions(Document hello) {
        if (hello == null) {
            return false;
        }
        if ("isdbgrid".equals(hello.getString("msg"))) {
            return true;
        }
        String setName = hello.getString("setName");
        return setName != null && !setName.isBlank();
    }

    static String describeTopology(Document hello) {
        if (hello == null) {
            return "unknown";
        }
        if ("isdbgrid".equals(hello.getString("msg"))) {
            return "mongos";
        }
        String setName = hello.getString("setName");
        return setName != null ? "replicaSet=" + setName : "standalone";
    }
}
