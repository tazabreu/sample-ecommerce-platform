package com.ecommerce.customer.config;

import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * Converters for PostgreSQL JSONB columns.
 */
public final class PostgresJsonConverters {

    private PostgresJsonConverters() {
    }

    @ReadingConverter
    public static class JsonbToStringConverter implements Converter<PGobject, String> {
        @Override
        public String convert(PGobject source) {
            return source == null ? null : source.getValue();
        }
    }

}
