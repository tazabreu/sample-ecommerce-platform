package com.ecommerce.order.config;

import com.ecommerce.shared.event.OrderCreatedEvent;
import com.ecommerce.shared.event.PaymentCompletedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration for Order Management Service.
 * Configured for reliable message processing with manual acknowledgment.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:order-service-group}")
    private String groupId;

    /**
     * Configure Kafka consumer factory for OrderCreatedEvent.
     *
     * @return configured consumer factory
     */
    @Bean
    public ConsumerFactory<String, OrderCreatedEvent> orderCreatedEventConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        
        // Connection
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        
        // Deserialization with error handling
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.ecommerce.*");
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.ecommerce.shared.event.OrderCreatedEvent");
        
        // Consumer behavior
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit
        config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Configure listener container factory for OrderCreatedEvent.
     *
     * @return configured listener container factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> orderCreatedEventListenerFactory(
            ConsumerFactory<String, OrderCreatedEvent> orderCreatedEventConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderCreatedEventConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setConcurrency(3); // 3 concurrent consumers
        return factory;
    }

    /**
     * Configure Kafka consumer factory for PaymentCompletedEvent.
     *
     * @return configured consumer factory
     */
    @Bean
    public ConsumerFactory<String, PaymentCompletedEvent> paymentCompletedEventConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        
        // Connection
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        
        // Deserialization with error handling
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.ecommerce.*");
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.ecommerce.shared.event.PaymentCompletedEvent");
        
        // Consumer behavior
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Configure listener container factory for PaymentCompletedEvent.
     *
     * @return configured listener container factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedEvent> paymentCompletedEventListenerFactory(
            ConsumerFactory<String, PaymentCompletedEvent> paymentCompletedEventConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedEvent> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentCompletedEventConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}


