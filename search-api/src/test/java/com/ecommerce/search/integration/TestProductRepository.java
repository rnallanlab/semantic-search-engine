package com.ecommerce.search.integration;

import com.ecommerce.search.model.Product;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

@Repository
@Profile("test")
public class TestProductRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RowMapper<Product> productRowMapper = new RowMapper<Product>() {
        @Override
        public Product mapRow(ResultSet rs, int rowNum) throws SQLException {
            Product product = new Product();
            product.setAsin(rs.getString("asin"));
            product.setTitle(rs.getString("title"));
            product.setDescription(rs.getString("description"));
            product.setBrand(rs.getString("brand"));

            // Handle category as JSON string in H2
            String categoryJson = rs.getString("category");
            if (categoryJson != null) {
                try {
                    List<String> categories = objectMapper.readValue(categoryJson, new TypeReference<List<String>>() {});
                    product.setCategory(categories);
                } catch (Exception e) {
                    product.setCategory(Arrays.asList());
                }
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

            // Calculate mock similarity for H2 (since we can't do vector operations)
            // In real PostgreSQL, this would be calculated via vector distance
            product.setSimilarity(0.75 + (Math.random() * 0.25)); // Random similarity between 0.75-1.0

            return product;
        }
    };

    public List<Product> searchByVector(String queryEmbedding, Double minSimilarity,
                                       String category, String brand, Double minPrice,
                                       Double maxPrice, Double minRating,
                                       Integer limit, Integer offset) {
        // H2 version: simulate vector search with simple text matching and filters
        StringBuilder sql = new StringBuilder("""
            SELECT * FROM products p
            WHERE 1=1
            """);

        if (category != null) {
            sql.append(" AND p.category LIKE ?");
        }
        if (brand != null) {
            sql.append(" AND LOWER(p.brand) LIKE LOWER(?)");
        }
        if (minPrice != null) {
            sql.append(" AND p.price >= ?");
        }
        if (maxPrice != null) {
            sql.append(" AND p.price <= ?");
        }
        if (minRating != null) {
            sql.append(" AND p.rating >= ?");
        }

        sql.append(" ORDER BY p.rating DESC, p.review_count DESC");
        sql.append(" LIMIT ? OFFSET ?");

        Object[] params = buildParams(category, brand, minPrice, maxPrice, minRating, limit, offset);

        return jdbcTemplate.query(sql.toString(), productRowMapper, params);
    }

    public Long countSearchResults(String queryEmbedding, Double minSimilarity,
                                  String category, String brand, Double minPrice,
                                  Double maxPrice, Double minRating) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*) FROM products p
            WHERE 1=1
            """);

        if (category != null) {
            sql.append(" AND p.category LIKE ?");
        }
        if (brand != null) {
            sql.append(" AND LOWER(p.brand) LIKE LOWER(?)");
        }
        if (minPrice != null) {
            sql.append(" AND p.price >= ?");
        }
        if (maxPrice != null) {
            sql.append(" AND p.price <= ?");
        }
        if (minRating != null) {
            sql.append(" AND p.rating >= ?");
        }

        Object[] params = buildCountParams(category, brand, minPrice, maxPrice, minRating);

        return jdbcTemplate.queryForObject(sql.toString(), Long.class, params);
    }

    public List<Product> searchByTitleVector(String queryEmbedding, Double minSimilarity,
                                           Integer limit, Integer offset) {
        // Simulate title-based search
        String sql = """
            SELECT * FROM products p
            WHERE p.title IS NOT NULL
            ORDER BY p.rating DESC
            LIMIT ? OFFSET ?
            """;

        return jdbcTemplate.query(sql, productRowMapper, limit, offset);
    }

    public List<Product> searchByDescriptionVector(String queryEmbedding, Double minSimilarity,
                                                 Integer limit, Integer offset) {
        // Simulate description-based search
        String sql = """
            SELECT * FROM products p
            WHERE p.description IS NOT NULL
            ORDER BY p.rating DESC
            LIMIT ? OFFSET ?
            """;

        return jdbcTemplate.query(sql, productRowMapper, limit, offset);
    }

    private Object[] buildParams(String category, String brand, Double minPrice,
                                Double maxPrice, Double minRating, Integer limit, Integer offset) {
        List<Object> params = new java.util.ArrayList<>();

        if (category != null) {
            params.add("%" + category + "%");
        }
        if (brand != null) {
            params.add("%" + brand + "%");
        }
        if (minPrice != null) {
            params.add(minPrice);
        }
        if (maxPrice != null) {
            params.add(maxPrice);
        }
        if (minRating != null) {
            params.add(minRating);
        }

        params.add(limit);
        params.add(offset);

        return params.toArray();
    }

    private Object[] buildCountParams(String category, String brand, Double minPrice,
                                    Double maxPrice, Double minRating) {
        List<Object> params = new java.util.ArrayList<>();

        if (category != null) {
            params.add("%" + category + "%");
        }
        if (brand != null) {
            params.add("%" + brand + "%");
        }
        if (minPrice != null) {
            params.add(minPrice);
        }
        if (maxPrice != null) {
            params.add(maxPrice);
        }
        if (minRating != null) {
            params.add(minRating);
        }

        return params.toArray();
    }
}