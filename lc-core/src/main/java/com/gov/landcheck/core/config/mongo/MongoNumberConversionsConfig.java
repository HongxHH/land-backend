package com.gov.landcheck.core.config.mongo;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.util.StringUtils;

/**
 * Spring Data MongoDB 4.5 起默认将  BigDecimal   BigInteger 以 BSON 字符串持久化，
 * 导致聚合/数值运算与业务预期不符。此处强制使用 Decimal128 
 */
@Configuration
public class MongoNumberConversionsConfig {

    @Bean
    MongoCustomConversions mongoCustomConversions() {
        return MongoCustomConversions.create(adapter -> {
            adapter.bigDecimal(MongoCustomConversions.BigDecimalRepresentation.DECIMAL128);
            adapter.registerConverter(LegacyStringToBigDecimalConverter.INSTANCE);
            adapter.registerConverter(LegacyStringToBigIntegerConverter.INSTANCE);
        });
    }

    @ReadingConverter
    private enum LegacyStringToBigDecimalConverter implements Converter<String, BigDecimal> {
        INSTANCE;

        @Override
        public BigDecimal convert(String source) {
            if (!StringUtils.hasText(source)) {
                return null;
            }
            return new BigDecimal(source.trim());
        }
    }

    @ReadingConverter
    private enum LegacyStringToBigIntegerConverter implements Converter<String, BigInteger> {
        INSTANCE;

        @Override
        public BigInteger convert(String source) {
            if (!StringUtils.hasText(source)) {
                return null;
            }
            return new BigInteger(source.trim());
        }
    }
}
