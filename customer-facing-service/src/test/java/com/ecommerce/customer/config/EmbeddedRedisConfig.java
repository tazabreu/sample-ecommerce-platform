package com.ecommerce.customer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test configuration that brings up a TestContainers Redis instance for integration tests.
 */
@TestConfiguration
public class EmbeddedRedisConfig {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedRedisConfig.class);

    private static final String LOCALHOST = "127.0.0.1";

    @Bean(destroyMethod = "stop")
    public GenericContainer<?> redisContainer() {
        GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--maxmemory", "128mb", "--save", "900", "1", "--save", "300", "10", "--save", "60", "10000", "--appendonly", "no");

        redis.start();
        logger.info("Started TestContainers Redis for tests on port {}", redis.getMappedPort(6379));
        return redis;
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory(GenericContainer<?> redisContainer) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(LOCALHOST);
        config.setPort(redisContainer.getMappedPort(6379));
        return new LettuceConnectionFactory(config);
    }
}
