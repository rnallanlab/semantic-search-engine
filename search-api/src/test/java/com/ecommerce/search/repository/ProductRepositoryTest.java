package com.ecommerce.search.repository;

import com.ecommerce.search.model.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ResultSet resultSet;

    @Mock
    private Array sqlArray;

    @InjectMocks
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSearchByVector_Success() throws SQLException {
        // Arrange
        Product expectedProduct = createTestProduct();
        List<Product> expectedProducts = Arrays.asList(expectedProduct);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(expectedProducts);

        // Act
        List<Product> result = productRepository.searchByVector(
                "[0.1,0.2,0.3]", 0.5, "Electronics", "Apple", 100.0, 1000.0, 4.0, 10, 0);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(expectedProduct.getAsin(), result.get(0).getAsin());
        assertEquals(expectedProduct.getTitle(), result.get(0).getTitle());

        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void testCountSearchResults_Success() {
        // Arrange
        Long expectedCount = 25L;
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(expectedCount);

        // Act
        Long result = productRepository.countSearchResults(
                "[0.1,0.2,0.3]", 0.5, "Electronics", "Apple", 100.0, 1000.0, 4.0);

        // Assert
        assertEquals(expectedCount, result);
        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), any(Object[].class));
    }

    @Test
    void testSearchByTitleVector_Success() {
        // Arrange
        Product expectedProduct = createTestProduct();
        List<Product> expectedProducts = Arrays.asList(expectedProduct);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(expectedProducts);

        // Act
        List<Product> result = productRepository.searchByTitleVector("[0.1,0.2,0.3]", 0.5, 10, 0);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(expectedProduct.getAsin(), result.get(0).getAsin());

        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void testSearchByDescriptionVector_Success() {
        // Arrange
        Product expectedProduct = createTestProduct();
        List<Product> expectedProducts = Arrays.asList(expectedProduct);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(expectedProducts);

        // Act
        List<Product> result = productRepository.searchByDescriptionVector("[0.1,0.2,0.3]", 0.5, 10, 0);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(expectedProduct.getAsin(), result.get(0).getAsin());

        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void testRowMapper_WithCompleteData() throws SQLException {
        // Arrange
        setupMockResultSet();

        // Act - Test the row mapper through the actual repository method
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper<Product> mapper = invocation.getArgument(1);
                    return Arrays.asList(mapper.mapRow(resultSet, 1));
                });

        List<Product> result = productRepository.searchByVector("[0.1,0.2,0.3]", 0.5, null, null, null, null, null, 10, 0);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        Product product = result.get(0);
        assertEquals("B123456789", product.getAsin());
        assertEquals("Test Product", product.getTitle());
        assertEquals("Test Description", product.getDescription());
        assertEquals("TestBrand", product.getBrand());
        assertEquals(Arrays.asList("Electronics", "Computers"), product.getCategory());
        assertEquals(299.99, product.getPrice());
        assertEquals("http://example.com/image.jpg", product.getImageUrl());
        assertEquals(4.5, product.getRating());
        assertEquals(100, product.getReviewCount());
        assertEquals(0.85, product.getSimilarity());
    }

    @Test
    void testRowMapper_WithNullValues() throws SQLException {
        // Arrange
        setupMockResultSetWithNulls();

        // Act
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper<Product> mapper = invocation.getArgument(1);
                    return Arrays.asList(mapper.mapRow(resultSet, 1));
                });

        List<Product> result = productRepository.searchByVector("[0.1,0.2,0.3]", 0.5, null, null, null, null, null, 10, 0);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        Product product = result.get(0);
        assertEquals("B123456789", product.getAsin());
        assertNull(product.getCategory());
        assertNull(product.getSimilarity());
    }

    private Product createTestProduct() {
        Product product = new Product();
        product.setAsin("B123456789");
        product.setTitle("Test Product");
        product.setDescription("Test Description");
        product.setBrand("TestBrand");
        product.setCategory(Arrays.asList("Electronics", "Computers"));
        product.setPrice(299.99);
        product.setImageUrl("http://example.com/image.jpg");
        product.setRating(4.5);
        product.setReviewCount(100);
        product.setSimilarity(0.85);
        return product;
    }

    private void setupMockResultSet() throws SQLException {
        when(resultSet.getString("asin")).thenReturn("B123456789");
        when(resultSet.getString("title")).thenReturn("Test Product");
        when(resultSet.getString("description")).thenReturn("Test Description");
        when(resultSet.getString("brand")).thenReturn("TestBrand");

        when(resultSet.getArray("category")).thenReturn(sqlArray);
        when(sqlArray.getArray()).thenReturn(new String[]{"Electronics", "Computers"});

        when(resultSet.getDouble("price")).thenReturn(299.99);
        when(resultSet.getString("image_url")).thenReturn("http://example.com/image.jpg");
        when(resultSet.getDouble("rating")).thenReturn(4.5);
        when(resultSet.getInt("review_count")).thenReturn(100);
        when(resultSet.getString("title_embedding")).thenReturn("[0.1,0.2,0.3]");
        when(resultSet.getString("description_embedding")).thenReturn("[0.4,0.5,0.6]");
        when(resultSet.getString("combined_embedding")).thenReturn("[0.7,0.8,0.9]");
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(LocalDateTime.now()));
        when(resultSet.getTimestamp("updated_at")).thenReturn(Timestamp.valueOf(LocalDateTime.now()));
        when(resultSet.getDouble("similarity")).thenReturn(0.85);
    }

    private void setupMockResultSetWithNulls() throws SQLException {
        when(resultSet.getString("asin")).thenReturn("B123456789");
        when(resultSet.getString("title")).thenReturn("Test Product");
        when(resultSet.getString("description")).thenReturn(null);
        when(resultSet.getString("brand")).thenReturn(null);
        when(resultSet.getArray("category")).thenReturn(null);
        when(resultSet.getDouble("price")).thenReturn(0.0);
        when(resultSet.getString("image_url")).thenReturn(null);
        when(resultSet.getDouble("rating")).thenReturn(0.0);
        when(resultSet.getInt("review_count")).thenReturn(0);
        when(resultSet.getString("title_embedding")).thenReturn(null);
        when(resultSet.getString("description_embedding")).thenReturn(null);
        when(resultSet.getString("combined_embedding")).thenReturn(null);
        when(resultSet.getTimestamp("created_at")).thenReturn(null);
        when(resultSet.getTimestamp("updated_at")).thenReturn(null);

        // Simulate SQLException when trying to get similarity (column doesn't exist)
        when(resultSet.getDouble("similarity")).thenThrow(new SQLException("Column not found"));
    }
}