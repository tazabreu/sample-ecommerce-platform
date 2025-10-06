package com.ecommerce.customer.config;

import com.ecommerce.customer.model.OrderCreatedOutbox;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

/**
 * Converters for OrderCreatedOutbox.Status enum.
 * Required for Spring Data JDBC to map VARCHAR status column to enum.
 */
public class OutboxStatusConverter {

    @ReadingConverter
    @Component
    public static class StatusReadingConverter implements Converter<String, OrderCreatedOutbox.Status> {
        @Override
        public OrderCreatedOutbox.Status convert(String source) {
            if (source == null || source.isEmpty()) {
                return OrderCreatedOutbox.Status.PENDING;
            }
            return OrderCreatedOutbox.Status.valueOf(source);
        }
    }

    @WritingConverter
    @Component
    public static class StatusWritingConverter implements Converter<OrderCreatedOutbox.Status, String> {
        @Override
        public String convert(OrderCreatedOutbox.Status source) {
            return source == null ? "PENDING" : source.name();
        }
    }
}
