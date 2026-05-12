package com.agentcart.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.datasource.pgvector.url")
public class PgVectorDataSourceConfig {

    // DataSource is NOT registered as a Spring bean to avoid conflicting with
    // DataSourceAutoConfiguration (@ConditionalOnMissingBean(DataSource.class)).
    // Flyway manages the connection internally.
    @Bean
    public ApplicationRunner pgVectorFlywayRunner(
            @Value("${spring.datasource.pgvector.url}") String url,
            @Value("${spring.datasource.pgvector.username}") String username,
            @Value("${spring.datasource.pgvector.password}") String password) {
        return args -> Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/pgvector-migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }
}