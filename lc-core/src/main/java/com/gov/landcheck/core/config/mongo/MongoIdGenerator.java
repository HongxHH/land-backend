package com.gov.landcheck.core.config.mongo;

import java.util.Map;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.gov.landcheck.core.config.global.ApplicationContextProvider;

/**
 * MongoDB 自增 ID 生成器
 */
@Component
public class MongoIdGenerator {

    private static final String SEQUENCE_COLLECTION = "sequence";
    private static final String ID_FIELD = "_id";
    private static final String SEQ_FIELD = "seq";

    /**
     * 获取下一个自增 ID
     *
     * @param collectionName 集合名称
     * @return 自增 ID
     */
    public static Long getNextId(String collectionName) {
        // MongoTemplate mongoTemplate = MongoDBConfig.mongoTemplate;
        MongoTemplate mongoTemplate = ApplicationContextProvider.getBean(MongoTemplate.class);


        if (mongoTemplate == null) {
            throw new IllegalStateException("MongoTemplate 未初始化，请确保 MongoDBConfig 已正确配置");
        }

        Query query = new Query(Criteria.where(ID_FIELD).is(collectionName));
        Update update = new Update().inc(SEQ_FIELD, 1);
        
        // 如果不存在则创建，并设置初始值为 1
        org.springframework.data.mongodb.core.FindAndModifyOptions options = 
            new org.springframework.data.mongodb.core.FindAndModifyOptions().returnNew(true).upsert(true);
        
        Map<String, Object> sequence = mongoTemplate.findAndModify(
            query, 
            update, 
            options, 
            Map.class, 
            SEQUENCE_COLLECTION
        );

        if (sequence == null || sequence.get(SEQ_FIELD) == null) {
            // 如果还是 null，说明是新创建的，返回 1
            return 1L;
        }

        Object seqValue = sequence.get(SEQ_FIELD);
        if (seqValue instanceof Number) {
            return ((Number) seqValue).longValue();
        }
        return Long.valueOf(seqValue.toString());
    }
}

