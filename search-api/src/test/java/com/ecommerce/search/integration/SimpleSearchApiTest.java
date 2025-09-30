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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class SimpleSearchApiTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private EmbeddingService embeddingService;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        searchService = new SearchService();
        // Use reflection to inject mocks since we can't use @InjectMocks with the real service
        try {
            java.lang.reflect.Field repoField = SearchService.class.getDeclaredField("productRepository");
            repoField.setAccessible(true);
            repoField.set(searchService, productRepository);

            java.lang.reflect.Field embeddingField = SearchService.class.getDeclaredField("embeddingService");
            embeddingField.setAccessible(true);
            embeddingField.set(searchService, embeddingService);
        } catch (Exception e) {
            fail("Failed to inject mocks: " + e.getMessage());
        }
    }

    @Test
    void testSemanticSearchWithMockedMLResponse() {
        // Arrange - Mock ML service response
        List<List<Double>> mockEmbeddings = Arrays.asList(
                Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0)
        );

        when(embeddingService.generateEmbedding("wireless headphones"))
                .thenReturn(Mono.just(mockEmbeddings));
        when(embeddingService.formatEmbeddingForPostgres(mockEmbeddings.get(0)))
                .thenReturn("[0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0]");

        // Mock database response
        List<Product> mockProducts = createMockProducts();
        when(productRepository.searchByVector(anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockProducts);
        when(productRepository.countSearchResults(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(10L);

        // Create search request
        SearchRequest request = new SearchRequest();
        request.setQuery("wireless headphones");
        request.setLimit(5);
        request.setOffset(0);
        request.setMinSimilarity(0.5);

        // Act
        SearchResponse response = searchService.semanticSearch(request);

        // Assert
        assertNotNull(response);
        assertEquals("wireless headphones", response.getQuery());
        assertEquals(5, response.getLimit());
        assertEquals(0, response.getOffset());
        assertEquals(10, response.getTotalCount());
        assertEquals(3, response.getResults().size()); // Based on our mock data
        assertTrue(response.getExecutionTimeMs() >= 0);

        // Verify that the embedding service was called correctly
        verify(embeddingService).generateEmbedding("wireless headphones");
        verify(embeddingService).formatEmbeddingForPostgres(mockEmbeddings.get(0));

        // Verify that the repository was called with correct parameters
        verify(productRepository).searchByVector(
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

        // Check product details
        SearchResponse.ProductResult firstProduct = response.getResults().get(0);
        assertNotNull(firstProduct);
        assertEquals("B001", firstProduct.getAsin());
        assertEquals("Wireless Bluetooth Headphones", firstProduct.getTitle());
        assertTrue(firstProduct.getSimilarity() > 0);

        System.out.println("✅ Semantic search test passed!");
        System.out.println("Query: " + response.getQuery());
        System.out.println("Results: " + response.getResults().size());
        System.out.println("Total count: " + response.getTotalCount());
        System.out.println("Execution time: " + response.getExecutionTimeMs() + "ms");
    }

    @Test
    void testSemanticSearchWithFilters() {
        // Arrange - Mock ML service response
        List<List<Double>> mockEmbeddings = Arrays.asList(
                Arrays.asList(0.2, 0.4, 0.6, 0.8, 1.0, 0.9, 0.7, 0.5, 0.3, 0.1)
        );

        when(embeddingService.generateEmbedding("smartphone"))
                .thenReturn(Mono.just(mockEmbeddings));
        when(embeddingService.formatEmbeddingForPostgres(mockEmbeddings.get(0)))
                .thenReturn("[0.2,0.4,0.6,0.8,1.0,0.9,0.7,0.5,0.3,0.1]");

        // Mock filtered database response
        List<Product> mockProducts = Arrays.asList(createAppleProduct());
        when(productRepository.searchByVector(anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockProducts);
        when(productRepository.countSearchResults(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1L);

        // Create search request with filters
        SearchRequest request = new SearchRequest();
        request.setQuery("smartphone");
        request.setLimit(10);
        request.setOffset(0);
        request.setMinSimilarity(0.6);
        request.setCategory("Electronics");
        request.setBrand("Apple");
        request.setMinPrice(500.0);
        request.setMaxPrice(1500.0);
        request.setMinRating(4.0);

        // Act
        SearchResponse response = searchService.semanticSearch(request);

        // Assert
        assertNotNull(response);
        assertEquals("smartphone", response.getQuery());
        assertEquals(1, response.getTotalCount());
        assertEquals(1, response.getResults().size());

        // Verify filters were passed correctly
        verify(productRepository).searchByVector(
                eq("[0.2,0.4,0.6,0.8,1.0,0.9,0.7,0.5,0.3,0.1]"),
                eq(0.6),
                eq("Electronics"), // category
                eq("Apple"),       // brand
                eq(500.0),         // minPrice
                eq(1500.0),        // maxPrice
                eq(4.0),           // minRating
                eq(10),            // limit
                eq(0)              // offset
        );

        System.out.println("✅ Filtered search test passed!");
        System.out.println("Applied filters: category=Electronics, brand=Apple, price=500-1500, rating>=4.0");
    }

    @Test
    void testTitleSearch() {
        // Arrange
        List<List<Double>> mockEmbeddings = Arrays.asList(
                Arrays.asList(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5)
        );

        when(embeddingService.generateEmbedding("iPhone"))
                .thenReturn(Mono.just(mockEmbeddings));
        when(embeddingService.formatEmbeddingForPostgres(mockEmbeddings.get(0)))
                .thenReturn("[0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5]");

        List<Product> mockProducts = Arrays.asList(createAppleProduct());
        when(productRepository.searchByTitleVector(anyString(), any(), any(), any()))
                .thenReturn(mockProducts);

        // Act
        SearchResponse response = searchService.searchByTitle("iPhone", 5, 0, 0.7);

        // Assert
        assertNotNull(response);
        assertEquals("iPhone", response.getQuery());
        assertEquals(1, response.getResults().size());

        verify(productRepository).searchByTitleVector(
                eq("[0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5]"),
                eq(0.7),
                eq(5),
                eq(0)
        );

        System.out.println("✅ Title search test passed!");
    }

    @Test
    void testDescriptionSearch() {
        // Arrange
        List<List<Double>> mockEmbeddings = Arrays.asList(
                Arrays.asList(0.3, 0.7, 0.1, 0.9, 0.4, 0.6, 0.2, 0.8, 0.5, 0.0)
        );

        when(embeddingService.generateEmbedding("portable"))
                .thenReturn(Mono.just(mockEmbeddings));
        when(embeddingService.formatEmbeddingForPostgres(mockEmbeddings.get(0)))
                .thenReturn("[0.3,0.7,0.1,0.9,0.4,0.6,0.2,0.8,0.5,0.0]");

        List<Product> mockProducts = Arrays.asList(createPortableProduct());
        when(productRepository.searchByDescriptionVector(anyString(), any(), any(), any()))
                .thenReturn(mockProducts);

        // Act
        SearchResponse response = searchService.searchByDescription("portable", 3, 0, 0.6);

        // Assert
        assertNotNull(response);
        assertEquals("portable", response.getQuery());
        assertEquals(1, response.getResults().size());

        verify(productRepository).searchByDescriptionVector(
                eq("[0.3,0.7,0.1,0.9,0.4,0.6,0.2,0.8,0.5,0.0]"),
                eq(0.6),
                eq(3),
                eq(0)
        );

        System.out.println("✅ Description search test passed!");
    }

    @Test
    void testEmbeddingServiceFailure() {
        // Arrange - Mock embedding service failure
        when(embeddingService.generateEmbedding(anyString()))
                .thenReturn(Mono.error(new RuntimeException("ML service unavailable")));

        SearchRequest request = new SearchRequest();
        request.setQuery("test query");
        request.setLimit(5);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            searchService.semanticSearch(request);
        });

        assertTrue(exception.getMessage().contains("Search failed"));
        verify(embeddingService).generateEmbedding("test query");
        verifyNoInteractions(productRepository);

        System.out.println("✅ Embedding service failure test passed!");
    }

    private List<Product> createMockProducts() {
        Product product1 = new Product();
        product1.setAsin("B001");
        product1.setTitle("Wireless Bluetooth Headphones");
        product1.setDescription("High-quality wireless headphones with noise cancellation");
        product1.setBrand("AudioTech");
        product1.setCategory(Arrays.asList("Electronics", "Audio"));
        product1.setPrice(199.99);
        product1.setRating(4.5);
        product1.setReviewCount(1250);
        product1.setSimilarity(0.85);

        Product product2 = new Product();
        product2.setAsin("B002");
        product2.setTitle("Gaming Headset");
        product2.setDescription("RGB gaming headset with surround sound");
        product2.setBrand("GameTech");
        product2.setCategory(Arrays.asList("Electronics", "Gaming"));
        product2.setPrice(129.99);
        product2.setRating(4.2);
        product2.setReviewCount(890);
        product2.setSimilarity(0.78);

        Product product3 = new Product();
        product3.setAsin("B003");
        product3.setTitle("Noise Cancelling Earbuds");
        product3.setDescription("Compact wireless earbuds with active noise cancelling");
        product3.setBrand("AudioPro");
        product3.setCategory(Arrays.asList("Electronics", "Audio"));
        product3.setPrice(149.99);
        product3.setRating(4.6);
        product3.setReviewCount(2100);
        product3.setSimilarity(0.82);

        return Arrays.asList(product1, product2, product3);
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
        product.setSimilarity(0.92);
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
        product.setSimilarity(0.75);
        return product;
    }
}