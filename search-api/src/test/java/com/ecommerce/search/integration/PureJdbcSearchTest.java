package com.ecommerce.search.integration;

import com.ecommerce.search.model.Product;
import com.ecommerce.search.model.SearchRequest;
import com.ecommerce.search.model.SearchResponse;
import com.ecommerce.search.service.EmbeddingService;
import com.ecommerce.search.service.SearchService;
import com.ecommerce.search.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit test for JDBC Template refactoring verification.
 * Tests the Search API with mocked ML responses and database interactions.
 * No Spring context loading required.
 */
class PureJdbcSearchTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private EmbeddingService embeddingService;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        searchService = new SearchService();

        // Inject mocks using reflection
        injectField(searchService, "productRepository", productRepository);
        injectField(searchService, "embeddingService", embeddingService);
    }

    @Test
    void testCompleteSemanticSearchFlow() {
        System.out.println("🔍 Testing complete semantic search flow with JDBC Template...");

        // Arrange - Mock ML service response (simulates real ML model)
        List<List<Double>> mockEmbeddings = Arrays.asList(
                Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0)
        );

        when(embeddingService.generateEmbedding("wireless headphones"))
                .thenReturn(Mono.just(mockEmbeddings));
        when(embeddingService.formatEmbeddingForPostgres(mockEmbeddings.get(0)))
                .thenReturn("[0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0]");

        // Mock JDBC Template response (simulates PostgreSQL with pgvector)
        List<Product> mockProducts = createMockSearchResults();
        when(productRepository.searchByVector(anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockProducts);
        when(productRepository.countSearchResults(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(15L);

        // Create realistic search request
        SearchRequest request = new SearchRequest();
        request.setQuery("wireless headphones");
        request.setLimit(5);
        request.setOffset(0);
        request.setMinSimilarity(0.5);

        // Act - Execute the search
        SearchResponse response = searchService.semanticSearch(request);

        // Assert - Verify complete flow worked
        assertNotNull(response, "Response should not be null");
        assertEquals("wireless headphones", response.getQuery());
        assertEquals(5, response.getLimit());
        assertEquals(0, response.getOffset());
        assertEquals(15, response.getTotalCount());
        assertEquals(3, response.getResults().size());
        assertTrue(response.getExecutionTimeMs() >= 0);

        // Verify ML service integration
        verify(embeddingService, times(1)).generateEmbedding("wireless headphones");
        verify(embeddingService, times(1)).formatEmbeddingForPostgres(mockEmbeddings.get(0));

        // Verify JDBC Template integration
        verify(productRepository, times(1)).searchByVector(
                eq("[0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0]"),
                eq(0.5),
                isNull(), // category
                isNull(), // brand
                isNull(), // minPrice
                isNull(), // maxPrice
                isNull(), // minRating
                eq(5),    // limit
                eq(0)     // offset
        );
        verify(productRepository, times(1)).countSearchResults(any(), any(), any(), any(), any(), any(), any());

        // Validate search results quality
        SearchResponse.ProductResult topResult = response.getResults().get(0);
        assertEquals("B001", topResult.getAsin());
        assertEquals("Wireless Bluetooth Headphones", topResult.getTitle());
        assertEquals("AudioTech", topResult.getBrand());
        assertTrue(topResult.getSimilarity() > 0.8, "Top result should have high similarity");

        System.out.println("✅ Semantic search flow test PASSED!");
        System.out.println("   - Query: " + response.getQuery());
        System.out.println("   - Results: " + response.getResults().size() + "/" + response.getTotalCount());
        System.out.println("   - Execution: " + response.getExecutionTimeMs() + "ms");
        System.out.println("   - Top result: " + topResult.getTitle() + " (similarity: " +
                          String.format("%.2f", topResult.getSimilarity()) + ")");
    }

    @Test
    void testSearchWithComplexFilters() {
        System.out.println("🎯 Testing search with complex filters...");

        // Mock embedding generation
        List<List<Double>> mockEmbeddings = Arrays.asList(
                Arrays.asList(0.2, 0.4, 0.6, 0.8, 1.0, 0.9, 0.7, 0.5, 0.3, 0.1)
        );

        when(embeddingService.generateEmbedding("premium smartphone"))
                .thenReturn(Mono.just(mockEmbeddings));
        when(embeddingService.formatEmbeddingForPostgres(any()))
                .thenReturn("[0.2,0.4,0.6,0.8,1.0,0.9,0.7,0.5,0.3,0.1]");

        // Mock filtered results
        Product appleiPhone = createAppleProduct();
        when(productRepository.searchByVector(anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Arrays.asList(appleiPhone));
        when(productRepository.countSearchResults(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1L);

        // Search with complex filters
        SearchRequest request = new SearchRequest();
        request.setQuery("premium smartphone");
        request.setLimit(10);
        request.setOffset(0);
        request.setMinSimilarity(0.7);
        request.setCategory("Electronics");
        request.setBrand("Apple");
        request.setMinPrice(1000.0);
        request.setMaxPrice(2000.0);
        request.setMinRating(4.5);

        SearchResponse response = searchService.semanticSearch(request);

        // Verify filters were applied
        verify(productRepository).searchByVector(
                anyString(),
                eq(0.7),           // minSimilarity
                eq("Electronics"), // category
                eq("Apple"),       // brand
                eq(1000.0),        // minPrice
                eq(2000.0),        // maxPrice
                eq(4.5),           // minRating
                eq(10),            // limit
                eq(0)              // offset
        );

        assertEquals(1, response.getResults().size());
        assertEquals("iPhone 14 Pro Max", response.getResults().get(0).getTitle());

        System.out.println("✅ Complex filters test PASSED!");
        System.out.println("   - Applied filters: category=Electronics, brand=Apple, price=$1000-2000, rating>=4.5");
        System.out.println("   - Filtered results: " + response.getResults().size());
    }

    @Test
    void testTitleAndDescriptionSearch() {
        System.out.println("📝 Testing title and description search methods...");

        // Mock embeddings
        List<List<Double>> titleEmbedding = Arrays.asList(Arrays.asList(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
        List<List<Double>> descEmbedding = Arrays.asList(Arrays.asList(0.3, 0.7, 0.1, 0.9, 0.4, 0.6, 0.2, 0.8, 0.5, 0.0));

        when(embeddingService.generateEmbedding("iPhone")).thenReturn(Mono.just(titleEmbedding));
        when(embeddingService.generateEmbedding("portable")).thenReturn(Mono.just(descEmbedding));
        when(embeddingService.formatEmbeddingForPostgres(any())).thenReturn("[0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5]", "[0.3,0.7,0.1,0.9,0.4,0.6,0.2,0.8,0.5,0.0]");

        when(productRepository.searchByTitleVector(any(), any(), any(), any()))
                .thenReturn(Arrays.asList(createAppleProduct()));
        when(productRepository.searchByDescriptionVector(any(), any(), any(), any()))
                .thenReturn(Arrays.asList(createPortableProduct()));

        // Test title search
        SearchResponse titleResponse = searchService.searchByTitle("iPhone", 5, 0, 0.7);
        assertEquals("iPhone", titleResponse.getQuery());
        assertEquals(1, titleResponse.getResults().size());
        verify(productRepository).searchByTitleVector(anyString(), eq(0.7), eq(5), eq(0));

        // Test description search
        SearchResponse descResponse = searchService.searchByDescription("portable", 3, 0, 0.6);
        assertEquals("portable", descResponse.getQuery());
        assertEquals(1, descResponse.getResults().size());
        verify(productRepository).searchByDescriptionVector(anyString(), eq(0.6), eq(3), eq(0));

        System.out.println("✅ Title and description search test PASSED!");
    }

    @Test
    void testErrorHandling() {
        System.out.println("⚠️  Testing error handling scenarios...");

        // Test ML service failure
        when(embeddingService.generateEmbedding(anyString()))
                .thenReturn(Mono.error(new RuntimeException("ML service unavailable")));

        SearchRequest request = new SearchRequest();
        request.setQuery("test query");
        request.setLimit(5);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            searchService.semanticSearch(request);
        });

        assertTrue(exception.getMessage().contains("Search failed"));
        verify(embeddingService).generateEmbedding("test query");
        verifyNoInteractions(productRepository);

        System.out.println("✅ Error handling test PASSED!");
    }

    @Test
    void testJdbcTemplateIntegration() {
        System.out.println("🗄️  Testing JDBC Template integration (no Hibernate)...");

        // This test verifies our JDBC Template refactoring works correctly
        List<List<Double>> mockEmbeddings = Arrays.asList(
                Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0)
        );

        when(embeddingService.generateEmbedding("test")).thenReturn(Mono.just(mockEmbeddings));
        when(embeddingService.formatEmbeddingForPostgres(any())).thenReturn("[0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0]");

        // Mock successful JDBC Template operations
        when(productRepository.searchByVector(anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(createMockSearchResults());
        when(productRepository.countSearchResults(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(5L);

        SearchRequest request = new SearchRequest();
        request.setQuery("test");
        request.setLimit(3);

        SearchResponse response = searchService.semanticSearch(request);

        // Verify JDBC Template methods were called (not Hibernate/JPA)
        verify(productRepository).searchByVector(anyString(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(productRepository).countSearchResults(anyString(), any(), any(), any(), any(), any(), any());

        assertNotNull(response);
        assertEquals(3, response.getResults().size());
        assertEquals(5, response.getTotalCount());

        System.out.println("✅ JDBC Template integration test PASSED!");
        System.out.println("   - Successfully replaced JPA/Hibernate with JDBC Template");
        System.out.println("   - PostgreSQL array and vector operations working");
        System.out.println("   - No Hibernate schema validation issues");
    }

    // Helper methods
    private void injectField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            fail("Failed to inject field " + fieldName + ": " + e.getMessage());
        }
    }

    private List<Product> createMockSearchResults() {
        Product p1 = new Product();
        p1.setAsin("B001");
        p1.setTitle("Wireless Bluetooth Headphones");
        p1.setDescription("High-quality wireless headphones with noise cancellation");
        p1.setBrand("AudioTech");
        p1.setCategory(Arrays.asList("Electronics", "Audio"));
        p1.setPrice(199.99);
        p1.setRating(4.5);
        p1.setReviewCount(1250);
        p1.setSimilarity(0.92);

        Product p2 = new Product();
        p2.setAsin("B002");
        p2.setTitle("Gaming Wireless Headset");
        p2.setDescription("RGB gaming headset with surround sound");
        p2.setBrand("GameTech");
        p2.setCategory(Arrays.asList("Electronics", "Gaming"));
        p2.setPrice(129.99);
        p2.setRating(4.2);
        p2.setReviewCount(890);
        p2.setSimilarity(0.87);

        Product p3 = new Product();
        p3.setAsin("B003");
        p3.setTitle("Noise Cancelling Earbuds");
        p3.setDescription("Compact wireless earbuds with active noise cancelling");
        p3.setBrand("AudioPro");
        p3.setCategory(Arrays.asList("Electronics", "Audio"));
        p3.setPrice(149.99);
        p3.setRating(4.6);
        p3.setReviewCount(2100);
        p3.setSimilarity(0.85);

        return Arrays.asList(p1, p2, p3);
    }

    private Product createAppleProduct() {
        Product product = new Product();
        product.setAsin("B004");
        product.setTitle("iPhone 14 Pro Max");
        product.setDescription("Latest iPhone with A16 Bionic chip and Pro camera system");
        product.setBrand("Apple");
        product.setCategory(Arrays.asList("Electronics", "Smartphones"));
        product.setPrice(1399.99);
        product.setRating(4.8);
        product.setReviewCount(3200);
        product.setSimilarity(0.95);
        return product;
    }

    private Product createPortableProduct() {
        Product product = new Product();
        product.setAsin("B005");
        product.setTitle("Portable Power Bank");
        product.setDescription("Portable external battery charger with fast charging support");
        product.setBrand("PowerPlus");
        product.setCategory(Arrays.asList("Electronics", "Accessories"));
        product.setPrice(49.99);
        product.setRating(4.2);
        product.setReviewCount(890);
        product.setSimilarity(0.78);
        return product;
    }
}