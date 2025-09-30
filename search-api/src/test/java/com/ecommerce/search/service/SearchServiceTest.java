package com.ecommerce.search.service;

import com.ecommerce.search.model.Product;
import com.ecommerce.search.model.SearchRequest;
import com.ecommerce.search.model.SearchResponse;
import com.ecommerce.search.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SearchServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private EmbeddingService embeddingService;

    @InjectMocks
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSemanticSearch_Success() {
        // Arrange
        SearchRequest request = new SearchRequest();
        request.setQuery("wireless headphones");
        request.setLimit(10);
        request.setOffset(0);
        request.setMinSimilarity(0.5);

        List<List<Double>> mockEmbeddings = Arrays.asList(
                Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5)
        );

        Product mockProduct = createTestProduct();
        List<Product> mockProducts = Arrays.asList(mockProduct);

        when(embeddingService.generateEmbedding("wireless headphones"))
                .thenReturn(Mono.just(mockEmbeddings));
        when(embeddingService.formatEmbeddingForPostgres(any()))
                .thenReturn("[0.1,0.2,0.3,0.4,0.5]");
        when(productRepository.searchByVector(anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockProducts);
        when(productRepository.countSearchResults(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(25L);

        // Act
        SearchResponse response = searchService.semanticSearch(request);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getResults().size());
        assertEquals(25, response.getTotalCount());
        assertEquals("wireless headphones", response.getQuery());
        assertTrue(response.getExecutionTimeMs() >= 0);

        verify(embeddingService).generateEmbedding("wireless headphones");
        verify(embeddingService).formatEmbeddingForPostgres(any());
        verify(productRepository).searchByVector(anyString(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(productRepository).countSearchResults(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void testSemanticSearch_EmbeddingServiceFailure() {
        // Arrange
        SearchRequest request = new SearchRequest();
        request.setQuery("test query");

        when(embeddingService.generateEmbedding("test query"))
                .thenReturn(Mono.just(Arrays.asList())); // Empty list

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            searchService.semanticSearch(request);
        });

        assertTrue(exception.getMessage().contains("Failed to generate embedding"));
        verify(embeddingService).generateEmbedding("test query");
        verifyNoInteractions(productRepository);
    }

    @Test
    void testSemanticSearch_RepositoryFailure() {
        // Arrange
        SearchRequest request = new SearchRequest();
        request.setQuery("test query");

        List<List<Double>> mockEmbeddings = Arrays.asList(
                Arrays.asList(0.1, 0.2, 0.3)
        );

        when(embeddingService.generateEmbedding("test query"))
                .thenReturn(Mono.just(mockEmbeddings));
        when(embeddingService.formatEmbeddingForPostgres(any()))
                .thenReturn("[0.1,0.2,0.3]");
        when(productRepository.searchByVector(anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            searchService.semanticSearch(request);
        });

        assertTrue(exception.getMessage().contains("Search failed"));
        verify(productRepository).searchByVector(anyString(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void testSearchByTitle_Success() {
        // Arrange
        String query = "iPhone";
        int limit = 5;
        int offset = 0;
        double minSimilarity = 0.7;

        List<List<Double>> mockEmbeddings = Arrays.asList(
                Arrays.asList(0.1, 0.2, 0.3)
        );

        Product mockProduct = createTestProduct();
        List<Product> mockProducts = Arrays.asList(mockProduct);

        when(embeddingService.generateEmbedding(query))
                .thenReturn(Mono.just(mockEmbeddings));
        when(embeddingService.formatEmbeddingForPostgres(any()))
                .thenReturn("[0.1,0.2,0.3]");
        when(productRepository.searchByTitleVector(anyString(), eq(minSimilarity), eq(limit), eq(offset)))
                .thenReturn(mockProducts);

        // Act
        SearchResponse response = searchService.searchByTitle(query, limit, offset, minSimilarity);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getResults().size());
        assertEquals(1, response.getTotalCount()); // For title search, total count equals results size
        assertEquals(query, response.getQuery());
        assertTrue(response.getExecutionTimeMs() >= 0);

        verify(embeddingService).generateEmbedding(query);
        verify(productRepository).searchByTitleVector(anyString(), eq(minSimilarity), eq(limit), eq(offset));
    }

    @Test
    void testSearchByDescription_Success() {
        // Arrange
        String query = "portable";
        int limit = 3;
        int offset = 5;
        double minSimilarity = 0.6;

        List<List<Double>> mockEmbeddings = Arrays.asList(
                Arrays.asList(0.4, 0.5, 0.6)
        );

        Product mockProduct = createTestProduct();
        List<Product> mockProducts = Arrays.asList(mockProduct);

        when(embeddingService.generateEmbedding(query))
                .thenReturn(Mono.just(mockEmbeddings));
        when(embeddingService.formatEmbeddingForPostgres(any()))
                .thenReturn("[0.4,0.5,0.6]");
        when(productRepository.searchByDescriptionVector(anyString(), eq(minSimilarity), eq(limit), eq(offset)))
                .thenReturn(mockProducts);

        // Act
        SearchResponse response = searchService.searchByDescription(query, limit, offset, minSimilarity);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getResults().size());
        assertEquals(1, response.getTotalCount());
        assertEquals(query, response.getQuery());
        assertTrue(response.getExecutionTimeMs() >= 0);

        verify(embeddingService).generateEmbedding(query);
        verify(productRepository).searchByDescriptionVector(anyString(), eq(minSimilarity), eq(limit), eq(offset));
    }

    @Test
    void testSearchByTitle_EmbeddingFailure() {
        // Arrange
        when(embeddingService.generateEmbedding(anyString()))
                .thenReturn(Mono.just(Arrays.asList())); // Empty list

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            searchService.searchByTitle("test", 10, 0, 0.5);
        });

        assertTrue(exception.getMessage().contains("Title search failed"));
        verifyNoInteractions(productRepository);
    }

    @Test
    void testSearchByDescription_EmbeddingFailure() {
        // Arrange
        when(embeddingService.generateEmbedding(anyString()))
                .thenReturn(Mono.just(Arrays.asList())); // Empty list

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            searchService.searchByDescription("test", 10, 0, 0.5);
        });

        assertTrue(exception.getMessage().contains("Description search failed"));
        verifyNoInteractions(productRepository);
    }

    private Product createTestProduct() {
        Product product = new Product();
        product.setAsin("B123456789");
        product.setTitle("Test Wireless Headphones");
        product.setDescription("High-quality wireless headphones with noise cancellation");
        product.setBrand("TestBrand");
        product.setCategory(Arrays.asList("Electronics", "Audio"));
        product.setPrice(199.99);
        product.setImageUrl("http://example.com/headphones.jpg");
        product.setRating(4.5);
        product.setReviewCount(250);
        product.setSimilarity(0.85);
        return product;
    }
}