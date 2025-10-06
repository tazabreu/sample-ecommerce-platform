package com.ecommerce.customer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;
import org.springframework.data.relational.core.mapping.NamingStrategy;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableJdbcAuditing
public class JdbcConfig extends AbstractJdbcConfiguration {

    @Bean
    NamingStrategy namingStrategy() {
        return new SnakeCaseNamingStrategy();
    }

    @Override
    protected List<Converter<?, ?>> userConverters() {
        List<Converter<?, ?>> converters = new ArrayList<>();
        converters.add(new OutboxStatusConverter.StatusReadingConverter());
        converters.add(new OutboxStatusConverter.StatusWritingConverter());
        converters.add(new PostgresJsonConverters.JsonbToStringConverter());
        return converters;
    }

    private static String toSnakeCase(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        char[] characters = value.toCharArray();
        for (int index = 0; index < characters.length; index++) {
            char current = characters[index];
            if (Character.isUpperCase(current)) {
                if (index > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(current));
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    private static class SnakeCaseNamingStrategy implements NamingStrategy {
        @Override
        public String getTableName(Class<?> type) {
            return toSnakeCase(type.getSimpleName());
        }

        @Override
        public String getColumnName(RelationalPersistentProperty property) {
            return toSnakeCase(property.getName());
        }
    }

}
