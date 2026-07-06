package com.gov.landcheck.core.config.query;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 各业务实体 Mongo 排序字段白名单（存储字段名 + 常见 API 别名经 {@link SafePageSort} 归一化后匹配）。
 */
public final class MongoSortFields {

        private MongoSortFields() {
        }

        /** 继承 {@link com.gov.landcheck.core.config.mongo.MongoIdEntity} 的通用字段 */
        private static final Set<String> BASE = Set.of(
                        "_id", "create_time", "update_time");

        /** 文件记录：含 upload_time */
        private static final Set<String> FILE_BASE = merge(BASE,
                        "upload_time", "file_size", "original_name");

        public static final Set<String> FILE_RECORD = FILE_BASE;

        public static final Set<String> PARSED_DATA = merge(BASE,
                        "header_id", "field_key", "source_page");

        public static final Set<String> PARSE_JOB = merge(BASE,
                        "job_status");

        public static final Set<String> OCR_RESULT = merge(BASE,
                        "file_record_id");

        public static final Set<String> PROJECT = merge(BASE,
                        "project_name", "project_time");

        public static final Set<String> CONTRACT = merge(BASE,
                        "contract_name", "sign_date");

        public static final Set<String> SURVEY_REPORT = merge(BASE,
                        "report_name", "phase");

        public static final Set<String> ROOM_INFO = merge(BASE,
                        "room_number", "room_level");

        public static final Set<String> PLANNING_REVIEW = merge(BASE,
                        "form_name");

        public static final Set<String> PLANNING_REVIEW_ROW = merge(BASE,
                        "row_index");

        public static final Set<String> PROJECT_PARTY_SUMMARY = merge(BASE,
                        "form_name");

        public static final Set<String> CAPACITY_INDICATOR = merge(BASE,
                        "form_name");

        private static Set<String> merge(Set<String> base, String... extra) {
                LinkedHashSet<String> merged = new LinkedHashSet<>(base);
                for (String field : extra) {
                        merged.add(field);
                }
                return Set.copyOf(merged);
        }
}
