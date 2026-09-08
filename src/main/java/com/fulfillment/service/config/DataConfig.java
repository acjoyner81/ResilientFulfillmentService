package com.fulfillment.service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.fulfillment.service.repository.jpa")
@EnableRedisRepositories(basePackages = "com.fulfillment.service.repository.redis")
public class DataConfig {
}
