package com.gov.landcheck.file.task.processor.receiver.parse;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 解析用常量：预编译正则、字段名映射、户室表列别名
 */
public final class ParseConstants {

        private ParseConstants() {
        }

        // ---------- 字段名映射：标准化字段名 -> 原始字段名 ----------
        private static final Map<String, String> FIELD_NAME_MAPPING = new HashMap<>();

        static {
                FIELD_NAME_MAPPING.put("contract_number", "合同编号");
                FIELD_NAME_MAPPING.put("parcel_number", "出让宗地编号");
                FIELD_NAME_MAPPING.put("transferor", "出让人");
                FIELD_NAME_MAPPING.put("transferee", "受让人");
                FIELD_NAME_MAPPING.put("total_area", "建筑总面积");
                FIELD_NAME_MAPPING.put("residential_area", "建筑住宅面积");
                FIELD_NAME_MAPPING.put("commercial_area", "建筑商业面积");
                FIELD_NAME_MAPPING.put("property_area_confirmation_notice_number", "房产面积确认告知书编号");
                FIELD_NAME_MAPPING.put("real_estate_survey_report_number", "房地产勘测报告书编号");
                FIELD_NAME_MAPPING.put("building_name", "建筑名称");
                FIELD_NAME_MAPPING.put("property_certificate", "不动产权证编号");
                FIELD_NAME_MAPPING.put("room_level", "层次");
                FIELD_NAME_MAPPING.put("room_number", "户室号");
                FIELD_NAME_MAPPING.put("building_area", "建筑面积");
                FIELD_NAME_MAPPING.put("inner_area", "套内面积");
                FIELD_NAME_MAPPING.put("balcony_area", "阳台面积");
                FIELD_NAME_MAPPING.put("shared_area", "分摊面积");
                FIELD_NAME_MAPPING.put("room_structure", "户室结构");
                FIELD_NAME_MAPPING.put("room_usage", "用途");
                FIELD_NAME_MAPPING.put("remark", "备注");
                FIELD_NAME_MAPPING.put("room_info_building_area_sum_from_ocr", "建筑面积合计");
                FIELD_NAME_MAPPING.put("room_info_inner_area_sum_from_ocr", "套内面积合计");
                FIELD_NAME_MAPPING.put("room_info_balcony_area_sum_from_ocr", "阳台面积合计");
                FIELD_NAME_MAPPING.put("room_info_shared_area_sum_from_ocr", "分摊面积合计");
        }

        public static Map<String, String> getFieldNameMapping() {
                return Collections.unmodifiableMap(FIELD_NAME_MAPPING);
        }

        // ---------- 户室表格列别名 ----------
        private static final Map<String, List<String>> ROOM_FIELD_ALIASES = new HashMap<>();

        static {
                ROOM_FIELD_ALIASES.put("层次", Arrays.asList("层次", "楼层", "层数", "楼层号"));
                ROOM_FIELD_ALIASES.put("户室号", Arrays.asList("户室号", "房间号", "房号", "室号"));
                ROOM_FIELD_ALIASES.put("建筑面积", Arrays.asList("建筑面积", "总面积", "建筑总面积", "计容面积"));
                ROOM_FIELD_ALIASES.put("套内面积", Arrays.asList("套内面积", "室内面积", "使用面积"));
                ROOM_FIELD_ALIASES.put("阳台面积", Arrays.asList("阳台面积", "阳台"));
                ROOM_FIELD_ALIASES.put("分摊面积", Arrays.asList("分摊面积", "公摊面积", "共用面积"));
                ROOM_FIELD_ALIASES.put("户室结构", Arrays.asList("户室结构", "结构", "房屋结构"));
                ROOM_FIELD_ALIASES.put("用途", Arrays.asList("用途", "使用性质", "房屋用途"));
                ROOM_FIELD_ALIASES.put("备注", Arrays.asList("备注", "说明", "注"));
        }

        public static Map<String, List<String>> getRoomFieldAliases() {
                return Collections.unmodifiableMap(ROOM_FIELD_ALIASES);
        }

        /** 页内是否含户室面积对照表标题 */
        public static final Pattern ROOM_TABLE_TITLE_PATTERN = Pattern.compile("户\\s*室\\s*面\\s*积\\s*对\\s*照\\s*表");

        // ---------- 实测报告相关正则（表格扫描优先，以下为兜底） ----------
        public static final Pattern SURVEY_CONCLUSION_PATTERN = Pattern.compile(
                        "勘\\s*测\\s*成\\s*果\\s*表.*?(?=\\n\\d+/\\d+|\\n===PAGE_BREAK===\\n)",
                        Pattern.DOTALL);
        public static final Pattern SURVEY_CONCLUSION_TITLE_PATTERN = Pattern.compile("勘\\s*测\\s*成\\s*果\\s*表");

        /** OCR 占位空页标记 */
        public static final String OCR_EMPTY_PAGE_MARKER = "*未找到Markdown内容*";

        public static final Pattern BUILDING_LOCATION_PATTERN = Pattern.compile(
                        "房屋坐落[^>]*>([^<]+)",
                        Pattern.CASE_INSENSITIVE);
        public static final Pattern BUILDING_LOCATION_INLINE_PATTERN = Pattern.compile(
                        "房屋坐落[：:]?\\s*([^<\\n$]+)",
                        Pattern.CASE_INSENSITIVE);
        /** 多测合一封面 LaTeX 下划线坐落 */
        public static final Pattern BUILDING_LOCATION_LATEX_PATTERN = Pattern.compile(
                        "房屋坐落[：:]?\\s*\\$?\\s*\\\\underline\\{\\s*\\\\text\\{([^}]+)\\}",
                        Pattern.CASE_INSENSITIVE);
        public static final Pattern PROPERTY_CERTIFICATE_PATTERN = Pattern.compile(
                        "土地权属来源证明材料[^>]*>([^<]+)",
                        Pattern.CASE_INSENSITIVE);
        public static final Pattern PROPERTY_CERTIFICATE_INLINE_PATTERN = Pattern.compile(
                        "土地权属来源证明材料[：:]?\\s*([^<\\n]+)",
                        Pattern.CASE_INSENSITIVE);
        /** 标签 td 与证号 td 分列（勘测成果表常见结构） */
        public static final Pattern PROPERTY_CERTIFICATE_ADJACENT_CELL_PATTERN = Pattern.compile(
                        "土地权属来源[\\s\\S]*?</td>\\s*<td[^>]*>([^<]+)",
                        Pattern.CASE_INSENSITIVE);
        public static final Pattern PROPERTY_AREA_CONFIRMATION_NOTICE_PATTERN = Pattern.compile(
                        "(?:房产面积)?确认告知书编号[：:]?\\s*([^\\n\\r<]+)");
        /** 告知书编号兜底（如长住建房字岳麓第2024902646号） */
        public static final Pattern PROPERTY_NOTICE_NUMBER_FALLBACK_PATTERN = Pattern.compile(
                        "(长住建房字[^\\n\\r<]{3,60}号)");
        /** 非告知书编号的业务流水特征（JGCL/FCYCH 等） */
        public static final Pattern NOTICE_REJECT_PATTERN = Pattern.compile(
                        "^\\d+[A-Z]{2,}$");
        public static final Pattern REAL_ESTATE_SURVEY_REPORT_PATTERN = Pattern.compile(
                        "(?:分栋报告编号|测绘业务编号|房地产勘测报告书编号|勘测报告书编号)[：:]?\\s*([^\\n\\r<]+)");
}
