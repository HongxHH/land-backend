package com.gov.landcheck.core.config.query;

import java.util.Set;

/**
 * 各业务实体 Mongo 排序字段白名单。
 */
public final class MongoSortFields {

        private MongoSortFields() {
        }

        public static final Set<String> FILE_RECORD = Set.of(
                        "upload_time", "createTime", "_id", "file_size", "original_name");

        public static final Set<String> PARSED_DATA = Set.of(
                        "createTime", "_id", "header_id", "field_key", "source_page");

        public static final Set<String> PARSE_JOB = Set.of(
                        "createTime", "_id", "job_status", "update_time");

        public static final Set<String> OCR_RESULT = Set.of(
                        "createTime", "_id", "file_record_id");

        public static final Set<String> PROJECT = Set.of(
                        "createTime", "_id", "project_name", "project_time");

        public static final Set<String> CONTRACT = Set.of(
                        "createTime", "_id", "contract_name", "sign_date");

        public static final Set<String> SURVEY_REPORT = Set.of(
                        "createTime", "_id", "report_name", "phase");

        public static final Set<String> ROOM_INFO = Set.of(
                        "createTime", "_id", "room_number", "room_level");

        public static final Set<String> PLANNING_REVIEW = Set.of(
                        "createTime", "_id", "form_name");

        public static final Set<String> PLANNING_REVIEW_ROW = Set.of(
                        "createTime", "_id", "row_index");

        public static final Set<String> PROJECT_PARTY_SUMMARY = Set.of(
                        "createTime", "_id", "form_name");

        public static final Set<String> CAPACITY_INDICATOR = Set.of(
                        "createTime", "_id", "form_name");
}
