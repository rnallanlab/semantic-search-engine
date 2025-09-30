package com.ecommerce.search.repository;

import com.ecommerce.search.model.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Repository
public class ProductRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<Product> productRowMapper = new RowMapper<Product>() {
        @Override
        public Product mapRow(ResultSet rs, int rowNum) throws SQLException {
            Product product = new Product();
            product.setAsin(rs.getString("asin"));
            product.setTitle(rs.getString("title"));
            product.setDescription(rs.getString("description"));
            product.setBrand(rs.getString("brand"));

            // Handle PostgreSQL array
            Array categoryArray = rs.getArray("category");
            if (categoryArray != null) {
                String[] categories = (String[]) categoryArray.getArray();
                product.setCategory(Arrays.asList(categories));
            }

            product.setPrice(rs.getDouble("price"));
            product.setImageUrl(rs.getString("image_url"));
            product.setRating(rs.getDouble("rating"));
            product.setReviewCount(rs.getInt("review_count"));
            product.setTitleEmbedding(rs.getString("title_embedding"));
            product.setDescriptionEmbedding(rs.getString("description_embedding"));
            product.setCombinedEmbedding(rs.getString("combined_embedding"));

            // Handle timestamps
            if (rs.getTimestamp("created_at") != null) {
                product.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            }
            if (rs.getTimestamp("updated_at") != null) {
                product.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
            }

            // Handle similarity if present
            try {
                product.setSimilarity(rs.getDouble("similarity"));
            } catch (SQLException e) {
                // Similarity column might not exist in all queries
                product.setSimilarity(null);
            }

            return product;
        }
    };

    public List<Product> searchByVector(String queryEmbedding, Double minSimilarity,
                                       String category, String brand, Double minPrice,
                                       Double maxPrice, Double minRating,
                                       Integer limit, Integer offset) {
        String sql = """
            SELECT p.*,
                   (1 - (p.combined_embedding <=> CAST(? AS vector))) AS similarity
            FROM products p
            WHERE (1 - (p.combined_embedding <=> CAST(? AS vector))) >= ?
            AND (?::text IS NULL OR ?::text = ANY(p.category))
            AND (?::text IS NULL OR LOWER(p.brand) LIKE LOWER(CONCAT('%', ?::text, '%')))
            AND (?::numeric IS NULL OR p.price >= ?::numeric)
            AND (?::numeric IS NULL OR p.price <= ?::numeric)
            AND (?::numeric IS NULL OR p.rating >= ?::numeric)
            ORDER BY similarity DESC
            LIMIT ? OFFSET ?
            """;

        return jdbcTemplate.query(sql, productRowMapper,
                queryEmbedding, queryEmbedding,
                minSimilarity,
                category, category,
                brand, brand,
                minPrice, minPrice,
                maxPrice, maxPrice,
                minRating, minRating,
                limit, offset);
    }

    public Long countSearchResults(String queryEmbedding, Double minSimilarity,
                                  String category, String brand, Double minPrice,
                                  Double maxPrice, Double minRating) {
        String sql = """
            SELECT COUNT(*)
            FROM products p
            WHERE (1 - (p.combined_embedding <=> CAST(? AS vector))) >= ?
            AND (?::text IS NULL OR ?::text = ANY(p.category))
            AND (?::text IS NULL OR LOWER(p.brand) LIKE LOWER(CONCAT('%', ?::text, '%')))
            AND (?::numeric IS NULL OR p.price >= ?::numeric)
            AND (?::numeric IS NULL OR p.price <= ?::numeric)
            AND (?::numeric IS NULL OR p.rating >= ?::numeric)
            """;

        return jdbcTemplate.queryForObject(sql, Long.class,
                queryEmbedding,
                minSimilarity,
                category, category,
                brand, brand,
                minPrice, minPrice,
                maxPrice, maxPrice,
                minRating, minRating);
    }

    public List<Product> searchByTitleVector(String queryEmbedding, Double minSimilarity,
                                           Integer limit, Integer offset) {
        String sql = """
            SELECT p.*,
                   (1 - (p.title_embedding <=> CAST(? AS vector))) AS similarity
            FROM products p
            WHERE (1 - (p.title_embedding <=> CAST(? AS vector))) >= ?
            ORDER BY similarity DESC
            LIMIT ? OFFSET ?
            """;

        return jdbcTemplate.query(sql, productRowMapper,
                queryEmbedding, queryEmbedding,
                minSimilarity, limit, offset);
    }

    public List<Product> searchByDescriptionVector(String queryEmbedding, Double minSimilarity,
                                                 Integer limit, Integer offset) {
        String sql = """
            SELECT p.*,
                   (1 - (p.description_embedding <=> CAST(? AS vector))) AS similarity
            FROM products p
            WHERE p.description_embedding IS NOT NULL
            AND (1 - (p.description_embedding <=> CAST(? AS vector))) >= ?
            ORDER BY similarity DESC
            LIMIT ? OFFSET ?
            """;

        return jdbcTemplate.query(sql, productRowMapper,
                queryEmbedding, queryEmbedding,
                minSimilarity, limit, offset);
    }
}