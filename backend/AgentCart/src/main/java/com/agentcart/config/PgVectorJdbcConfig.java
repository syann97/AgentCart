package com.agentcart.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
@ConditionalOnProperty(name = "spring.datasource.pgvector.url")
public class PgVectorJdbcConfig {

    // DataSource is NOT registered as a Spring bean to avoid suppressing
    // MySQL's DataSourceAutoConfiguration (@ConditionalOnMissingBean(DataSource.class)).
    // HikariConfig constructor triggers eager pool initialization at context startup.
    @Bean("pgVectorJdbcTemplate")
    public NamedParameterJdbcTemplate pgVectorJdbcTemplate(
            @Value("${spring.datasource.pgvector.url}") String url,
            @Value("${spring.datasource.pgvector.username}") String username,
            @Value("${spring.datasource.pgvector.password}") String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("HikariPool-pgvector");
        return new NamedParameterJdbcTemplate(new HikariDataSource(config));
    }
}