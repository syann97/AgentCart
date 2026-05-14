package com.agentcart.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "spring.datasource.pgvector.url")
public class PgVectorJdbcConfig {

    // DataSource is created locally (not registered as a bean) to avoid suppressing
    // MySQL's DataSourceAutoConfiguration (@ConditionalOnMissingBean(DataSource.class)).
    @Bean("pgVectorJdbcTemplate")
    public NamedParameterJdbcTemplate pgVectorJdbcTemplate(
            @Value("${spring.datasource.pgvector.url}") String url,
            @Value("${spring.datasource.pgvector.username}") String username,
            @Value("${spring.datasource.pgvector.password}") String password) {
        DataSource dataSource = DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .build();
        return new NamedParameterJdbcTemplate(dataSource);
    }
}