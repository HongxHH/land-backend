package com.gov.landcheck.core.config.mongo;

import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.gov.landcheck.core.config.global.ApplicationContextProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * MongoDB 自增 ID 生成器
 */
@Slf4j
@Component
public class MongoIdGenerator {

    private static final String SEQUENCE_COLLECTION = "sequence";
    private static final String ID_FIELD = "_id";
    private static final String SEQ_FIELD = "seq";
    private static final int MAX_COLLISION_RETRIES = 8;

    /**
     * 获取下一个自增 ID
     *
     * @param collectionName 集合名称
     * @return 自增 ID
     */
    public static Long getNextId(String collectionName) {
        MongoTemplate mongoTemplate = ApplicationContextProvider.getBean(MongoTemplate.class);

        if (mongoTemplate == null) {
            throw new IllegalStateException("MongoTemplate 未初始化，请确保 MongoDBConfig 已正确配置");
        }

        for (int attempt = 0; attempt < MAX_COLLISION_RETRIES; attempt++) {
            Long nextId = incrementSequence(mongoTemplate, collectionName);
            if (!idExistsInCollection(mongoTemplate, collectionName, nextId)) {
                return nextId;
            }

            // sequence 落后于已有 _id：追平到集合内最大值后重试（避免 save 覆盖同 _id 文档）
            log.warn("集合 {} 自增 ID {} 已存在，正在将 sequence 追平至最大 _id 后重试（attempt={}）",
                    collectionName, nextId, attempt + 1);
            syncSequenceWithMaxExistingId(mongoTemplate, collectionName);
        }

        throw new IllegalStateException(
                "无法为集合 " + collectionName + " 生成唯一自增 ID，请检查 sequence 与现有 _id 是否一致");
    }

    private static Long incrementSequence(MongoTemplate mongoTemplate, String collectionName) {
        Query query = new Query(Criteria.where(ID_FIELD).is(collectionName));
        Update update = new Update().inc(SEQ_FIELD, 1);
        FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true).upsert(true);

        Map<String, Object> sequence = mongoTemplate.findAndModify(
                query,
                update,
                options,
                Map.class,
                SEQUENCE_COLLECTION);

        if (sequence == null || sequence.get(SEQ_FIELD) == null) {
            return 1L;
        }

        return toLong(sequence.get(SEQ_FIELD));
    }

    /**
     * 将 sequence 追平至集合内最大 _id（仅当 currentSeq &lt; maxId 时写入，不会回退计数器）。
     */
    private static void syncSequenceWithMaxExistingId(MongoTemplate mongoTemplate, String collectionName) {
        Long maxExistingId = findMaxExistingId(mongoTemplate, collectionName);
        if (maxExistingId == null) {
            return;
        }
        forceSequenceAtLeast(mongoTemplate, collectionName, maxExistingId);
    }

    private static void forceSequenceAtLeast(MongoTemplate mongoTemplate, String collectionName, long minSeq) {
        Query query = new Query(Criteria.where(ID_FIELD).is(collectionName).and(SEQ_FIELD).lt(minSeq));
        Update update = new Update().set(SEQ_FIELD, minSeq);
        FindAndModifyOptions options = new FindAndModifyOptions().upsert(true).returnNew(true);
        Map<String, Object> updated = mongoTemplate.findAndModify(
                query, update, options, Map.class, SEQUENCE_COLLECTION);

        if (updated == null) {
            // sequence 文档不存在，或 currentSeq 已 >= minSeq：仅在缺失时补建
            Query missingQuery = new Query(Criteria.where(ID_FIELD).is(collectionName));
            if (!mongoTemplate.exists(missingQuery, SEQUENCE_COLLECTION)) {
                mongoTemplate.findAndModify(
                        missingQuery,
                        new Update().set(SEQ_FIELD, minSeq),
                        new FindAndModifyOptions().upsert(true),
                        Map.class,
                        SEQUENCE_COLLECTION);
            }
        }
    }

    private static Long findMaxExistingId(MongoTemplate mongoTemplate, String collectionName) {
        Query maxQuery = new Query().with(Sort.by(Sort.Direction.DESC, ID_FIELD)).limit(1);
        Map<String, Object> maxDoc = mongoTemplate.findOne(maxQuery, Map.class, collectionName);
        if (maxDoc == null || maxDoc.get(ID_FIELD) == null) {
            return null;
        }
        return toLong(maxDoc.get(ID_FIELD));
    }

    private static boolean idExistsInCollection(MongoTemplate mongoTemplate, String collectionName, Long id) {
        Query query = new Query(Criteria.where(ID_FIELD).is(id));
        return mongoTemplate.exists(query, collectionName);
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
