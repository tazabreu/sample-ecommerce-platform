package com.ecommerce.order.config;

import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.model.PaymentStatus;
import com.ecommerce.order.model.ShippingAddress;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;
import org.springframework.data.relational.core.mapping.NamingStrategy;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.jdbc.core.SqlParameterValue;

import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;

@Configuration
@EnableJdbcAuditing
public class JdbcConfig extends AbstractJdbcConfiguration {

    private final ObjectMapper objectMapper;

    public JdbcConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected List<?> userConverters() {
        return List.of(
            new ShippingAddressWritingConverter(),
            new ShippingAddressReadingConverter(),
            OrderStatusWritingConverter.INSTANCE,
            OrderStatusReadingConverter.INSTANCE,
            PaymentStatusWritingConverter.INSTANCE,
            PaymentStatusReadingConverter.INSTANCE
        );
    }

    @Bean
    NamingStrategy namingStrategy() {
        return new SnakeCaseNamingStrategy();
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

    /**
     * Converts String to PGobject (JSONB) when writing to database.
     * Used for Order.shippingAddressJson field.
     */
    @WritingConverter
    private enum JsonbStringWritingConverter implements Converter<String, PGobject> {
        INSTANCE;

        @Override
        public PGobject convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            try {
                jsonObject.setValue(source);
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to convert String to JSONB PGobject", exception);
            }
            return jsonObject;
        }
    }

    /**
     * Converts PGobject (JSONB) to String when reading from database.
     * Used for Order.shippingAddressJson field.
     */
    @ReadingConverter
    private enum JsonbStringReadingConverter implements Converter<PGobject, String> {
        INSTANCE;

        @Override
        public String convert(PGobject source) {
            if (source == null) {
                return null;
            }
            // Only convert if it's actually a JSONB type
            if ("jsonb".equals(source.getType()) || "json".equals(source.getType())) {
                return source.getValue();
            }
            return null;
        }
    }

    /**
     * Converts Java OrderStatus enum to String when writing to database.
     * PostgreSQL will handle the implicit conversion from String to order_status ENUM.
     * This enables proper type handling for nullable enum parameters in queries.
     */
    @WritingConverter
    private enum OrderStatusWritingConverter implements Converter<OrderStatus, SqlParameterValue> {
        INSTANCE;

        @Override
        public SqlParameterValue convert(OrderStatus source) {
            return source == null
                    ? new SqlParameterValue(Types.OTHER, null)
                    : new SqlParameterValue(Types.OTHER, source.name());
        }
    }

    /**
     * Converts PostgreSQL ENUM (PGobject) to Java OrderStatus enum when reading from database.
     */
    @ReadingConverter
    private enum OrderStatusReadingConverter implements Converter<PGobject, OrderStatus> {
        INSTANCE;

        @Override
        public OrderStatus convert(PGobject source) {
            if (source == null || source.getValue() == null) {
                return null;
            }
            // Only convert if it's actually an order_status enum type
            if ("order_status".equals(source.getType())) {
                return OrderStatus.valueOf(source.getValue());
            }
            return null;
        }
    }

    /**
     * Converts Java PaymentStatus enum to String when writing to database.
     * PostgreSQL will handle the implicit conversion from String to payment_status ENUM.
     * This enables proper type handling for nullable enum parameters in queries.
     */
    @WritingConverter
    private enum PaymentStatusWritingConverter implements Converter<PaymentStatus, SqlParameterValue> {
        INSTANCE;

        @Override
        public SqlParameterValue convert(PaymentStatus source) {
            return source == null
                    ? new SqlParameterValue(Types.OTHER, null)
                    : new SqlParameterValue(Types.OTHER, source.name());
        }
    }

    /**
     * Converts PostgreSQL ENUM (PGobject) to Java PaymentStatus enum when reading from database.
     */
    @ReadingConverter
    private enum PaymentStatusReadingConverter implements Converter<PGobject, PaymentStatus> {
        INSTANCE;

        @Override
        public PaymentStatus convert(PGobject source) {
            if (source == null || source.getValue() == null) {
                return null;
            }
            // Only convert if it's actually a payment_status enum type
            if ("payment_status".equals(source.getType())) {
                return PaymentStatus.valueOf(source.getValue());
            }
            return null;
        }
    }

    /**
     * Converts ShippingAddress to PGobject (JSONB) when writing to database.
     */
    @WritingConverter
    private final class ShippingAddressWritingConverter implements Converter<ShippingAddress, PGobject> {

        @Override
        public PGobject convert(ShippingAddress source) {
            if (source == null) {
                return null;
            }
            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            try {
                String json = objectMapper.writeValueAsString(source.toMap());
                jsonObject.setValue(json);
            } catch (SQLException | JsonProcessingException exception) {
                throw new IllegalStateException("Failed to convert ShippingAddress to JSONB PGobject", exception);
            }
            return jsonObject;
        }
    }

    /**
     * Converts PGobject (JSONB) to ShippingAddress when reading from database.
     */
    @ReadingConverter
    private final class ShippingAddressReadingConverter implements Converter<PGobject, ShippingAddress> {

        @Override
        public ShippingAddress convert(PGobject source) {
            if (source == null || source.getValue() == null) {
                return null;
            }
            // Only convert if it's actually a JSONB type
            if ("jsonb".equals(source.getType()) || "json".equals(source.getType())) {
                try {
                    Map<String, String> addressMap = objectMapper.readValue(
                        source.getValue(),
                        new TypeReference<Map<String, String>>() {}
                    );
                    return new ShippingAddress(addressMap);
                } catch (JsonProcessingException exception) {
                    throw new IllegalStateException("Failed to convert JSONB PGobject to ShippingAddress", exception);
                }
            }
            return null;
        }
    }
}
