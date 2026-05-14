package com.agentcart.product.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
class ProductMigrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.2-pg17")
            .withDatabaseName("agentcart")
            .withUsername("postgres")
            .withPassword("postgres");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.pgvector.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.pgvector.username", postgres::getUsername);
        registry.add("spring.datasource.pgvector.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    // MySQL primary datasource (autoconfigured via @ServiceConnection)
    @Autowired
    private DataSource dataSource;

    // ── MySQL: products 테이블 ─────────────────────────────────────────────────

    @Test
    @DisplayName("products 테이블이 생성되고 모든 컬럼을 포함한다")
    void products_table_has_all_required_columns() throws Exception {
        Set<String> columns = getMysqlColumns("products");
        assertThat(columns).contains(
                "id", "name", "description", "price",
                "category", "brand", "stock", "status",
                "created_at", "updated_at"
        );
    }

    @Test
    @DisplayName("products 테이블의 category, status, brand 인덱스가 존재한다")
    void products_table_has_expected_indexes() throws Exception {
        Set<String> indexes = getMysqlIndexes("products");
        assertThat(indexes).contains(
                "idx_products_category",
                "idx_products_status",
                "idx_products_brand"
        );
    }

    // ── PostgreSQL: product_embeddings 테이블 ─────────────────────────────────

    @Test
    @DisplayName("product_embeddings 테이블이 생성되고 모든 컬럼을 포함한다")
    void product_embeddings_table_has_all_required_columns() throws Exception {
        Set<String> columns = getPgColumns("product_embeddings");
        assertThat(columns).contains("id", "product_id", "embedding", "model", "created_at");
    }

    @Test
    @DisplayName("product_embeddings 테이블에 ivfflat 인덱스가 존재한다")
    void product_embeddings_has_ivfflat_index() throws Exception {
        try (Connection conn = pgConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT indexname FROM pg_indexes " +
                     "WHERE tablename = 'product_embeddings' " +
                     "AND indexname = 'idx_product_embeddings_ivfflat'")) {
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).as("ivfflat 인덱스가 존재해야 한다").isTrue();
        }
    }

    @Test
    @DisplayName("product_id 컬럼에 UNIQUE 제약이 적용되어 있다")
    void product_embeddings_product_id_is_unique() throws Exception {
        try (Connection conn = pgConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM pg_indexes " +
                     "WHERE tablename = 'product_embeddings' " +
                     "AND indexname = 'product_embeddings_product_id_key'")) {
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).as("product_id UNIQUE 제약이 존재해야 한다").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("pgvector 확장이 활성화되어 있다")
    void pgvector_extension_is_enabled() throws Exception {
        try (Connection conn = pgConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'")) {
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).as("vector 확장이 활성화되어 있어야 한다").isEqualTo(1);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Connection pgConnection() throws Exception {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private Set<String> getMysqlColumns(String tableName) throws Exception {
        Set<String> columns = new HashSet<>();
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, null)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
        }
        return columns;
    }

    private Set<String> getMysqlIndexes(String tableName) throws Exception {
        Set<String> indexes = new HashSet<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.STATISTICS " +
                     "WHERE TABLE_NAME = ? AND TABLE_SCHEMA = DATABASE()")) {
            ps.setString(1, tableName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                indexes.add(rs.getString("INDEX_NAME").toLowerCase());
            }
        }
        return indexes;
    }

    private Set<String> getPgColumns(String tableName) throws Exception {
        Set<String> columns = new HashSet<>();
        try (Connection conn = pgConnection();
             ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, null)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
        }
        return columns;
    }
}
