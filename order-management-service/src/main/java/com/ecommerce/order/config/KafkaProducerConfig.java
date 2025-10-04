package com.ecommerce.order.config;

import com.ecommerce.shared.event.PaymentCompletedEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration for Order Management Service.
 * Configured for idempotent, reliable message delivery.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Configure Kafka producer factory for PaymentCompletedEvent.
     *
     * @return configured producer factory
     */
    @Bean
    public ProducerFactory<String, PaymentCompletedEvent> paymentCompletedEventProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        
        // Connection
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Serialization
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Reliability
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        
        // Performance
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        
        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * Create KafkaTemplate for sending PaymentCompletedEvent messages.
     *
     * @param producerFactory the producer factory
     * @return configured Kafka template
     */
    @Bean
    public KafkaTemplate<String, PaymentCompletedEvent> paymentCompletedEventKafkaTemplate(
            ProducerFactory<String, PaymentCompletedEvent> paymentCompletedEventProducerFactory
    ) {
        return new KafkaTemplate<>(paymentCompletedEventProducerFactory);
    }
}


