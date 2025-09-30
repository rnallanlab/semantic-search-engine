package com.ecommerce.search.integration;

import com.ecommerce.search.model.SearchRequest;
import com.ecommerce.search.model.SearchResponse;
import com.ecommerce.search.service.EmbeddingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebMvc
@ActiveProfiles("test")
class SearchApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmbeddingService embeddingService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testHealthEndpoint() throws Exception {
        // Mock the embedding service health check
        when(embeddingService.checkHealth())
                .thenReturn(Mono.just(true));

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.embeddingService").value(true))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testHealthEndpoint_EmbeddingServiceDown() throws Exception {
        // Mock the embedding service as unhealthy
        when(embeddingService.checkHealth())
                .thenReturn(Mono.just(false));

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.embeddingService").value(false));
    }

    @Test
    void testHealthEndpoint_EmbeddingServiceError() throws Exception {
        // Mock the embedding service throwing an error
        when(embeddingService.checkHealth())
                .thenReturn(Mono.error(new RuntimeException("ML service unavailable")));

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("unhealthy"))
                .andExpect(jsonPath("$.error").value("ML service unavailable"));
    }

    @Test
    void testSemanticSearch_WithMockedEmbedding() throws Exception {
        // Mock embedding service response
        List<List<Double>> mockEmbeddings = Arrays.asList(
                Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0)
        );

        when(embeddingService.generateEmbedding("wireless headphones"))
                .thenReturn(Mono.just(mockEmbeddings));
        when(embeddingService.formatEmbeddingForPostgres(mockEmbeddings.get(0)))
                .thenReturn("[0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0]");

        // Create search request
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setQuery("wireless headphones");
        searchRequest.setLimit(5);
        searchRequest.setOffset(0);
        searchRequest.setMinSimilarity(0.5);

        String requestJson = objectMapper.writeValueAsString(searchRequest);

        // Perform the search request
        MvcResult result = mockMvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "demo-key-123")
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.query").value("wireless headphones"))
                .andExpect(jsonPath("$.limit").value(5))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.executionTimeMs").exists())
                .andExpect(jsonPath("$.totalCount").exists())
                .andExpect(jsonPath("$.results").isArray())
                .andReturn();

        // Parse and validate the response
        String responseJson = result.getResponse().getContentAsString();
        SearchResponse response = objectMapper.readValue(responseJson, SearchResponse.class);

        assertNotNull(response);
        assertEquals("wireless headphones", response.getQuery());
        assertEquals(5, response.getLimit());
        assertEquals(0, response.getOffset());
        assertTrue(response.getExecutionTimeMs() >= 0);
        assertNotNull(response.getResults());

        // Log the response for debugging
        System.out.println("Search Response: " + responseJson);
        System.out.println("Total results found: " + response.getTotalCount());
        System.out.println("Number of results returned: " + response.getResults().size());
    }

    @Test
    void testSemanticSearch_WithFilters() throws Exception {
        // Mock embedding service response
        List<List<Double>> mockEmbeddings = Arrays.asList(
                Arrays.asList(0.2, 0.4, 0.6, 0.8, 1.0, 0.9, 0.7, 0.5, 0.3, 0.1)
        );

        when(embeddingService.generateEmbedding("smartphone"))
                .thenReturn(Mono.just(mockEmbeddings));
        when(embeddingService.formatEmbeddingForPostgres(mockEmbeddings.get(0)))
                .thenReturn("[0.2,0.4,0.6,0.8,1.0,0.9,0.7,0.5,0.3,0.1]");

        // Create search request with filters
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setQuery("smartphone");
        searchRequest.setLimit(10);
        searchRequest.setOffset(0);
        searchRequest.setMinSimilarity(0.6);
        searchRequest.setCategory("Electronics");
        searchRequest.setBrand("Apple");
        searchRequest.setMinPrice(500.0);
        searchRequest.setMaxPrice(1500.0);
        searchRequest.setMinRating(4.0);

        String requestJson = objectMapper.writeValueAsString(searchRequest);

        // Perform the search request
        mockMvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "demo-key-123")
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.query").value("smartphone"))
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.offset").value(0));
    }

    @Test
    void testTitleSearch_WithMockedEmbedding() throws Exception {
        // Mock embedding service response
        List<List<Double>> mockEmbeddings = Arrays.asList(
                Arrays.asList(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5)
        );

        when(embeddingService.generateEmbedding("iPhone"))
                .thenReturn(Mono.just(mockEmbeddings));
        when(embeddingService.formatEmbeddingForPostgres(mockEmbeddings.get(0)))
                .thenReturn("[0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5]");

        // Create request body for title search
        String requestBody = """
                {
                    "query": "iPhone",
                    "limit": 5,
                    "offset": 0,
                    "minSimilarity": 0.7
                }
                """;

        mockMvc.perform(post("/api/v1/search/title")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "demo-key-123")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.query").value("iPhone"))
                .andExpect(jsonPath("$.limit").value(5))
                .andExpect(jsonPath("$.results").isArray());
    }

    @Test
    void testDescriptionSearch_WithMockedEmbedding() throws Exception {
        // Mock embedding service response
        List<List<Double>> mockEmbeddings = Arrays.asList(
                Arrays.asList(0.3, 0.7, 0.1, 0.9, 0.4, 0.6, 0.2, 0.8, 0.5, 0.0)
        );

        when(embeddingService.generateEmbedding("portable"))
                .thenReturn(Mono.just(mockEmbeddings));
        when(embeddingService.formatEmbeddingForPostgres(mockEmbeddings.get(0)))
                .thenReturn("[0.3,0.7,0.1,0.9,0.4,0.6,0.2,0.8,0.5,0.0]");

        // Create request body for description search
        String requestBody = """
                {
                    "query": "portable",
                    "limit": 3,
                    "offset": 0,
                    "minSimilarity": 0.6
                }
                """;

        mockMvc.perform(post("/api/v1/search/description")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "demo-key-123")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.query").value("portable"))
                .andExpect(jsonPath("$.limit").value(3))
                .andExpect(jsonPath("$.results").isArray());
    }

    @Test
    void testSemanticSearch_InvalidRequest() throws Exception {
        // Test with invalid request (missing query)
        String invalidRequestBody = """
                {
                    "limit": 5,
                    "offset": 0
                }
                """;

        mockMvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "demo-key-123")
                        .content(invalidRequestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSemanticSearch_MissingApiKey() throws Exception {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setQuery("test");
        searchRequest.setLimit(5);

        String requestJson = objectMapper.writeValueAsString(searchRequest);

        mockMvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testSemanticSearch_EmbeddingServiceFailure() throws Exception {
        // Mock embedding service failure
        when(embeddingService.generateEmbedding(anyString()))
                .thenReturn(Mono.error(new RuntimeException("ML service unavailable")));

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setQuery("test query");
        searchRequest.setLimit(5);

        String requestJson = objectMapper.writeValueAsString(searchRequest);

        mockMvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "demo-key-123")
                        .content(requestJson))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testRootEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.service").value("Semantic Search API"))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.status").value("running"));
    }
}