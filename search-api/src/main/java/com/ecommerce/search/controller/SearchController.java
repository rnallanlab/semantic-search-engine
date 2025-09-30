package com.ecommerce.search.controller;

import com.ecommerce.search.model.SearchRequest;
import com.ecommerce.search.model.SearchResponse;
import com.ecommerce.search.service.EmbeddingService;
import com.ecommerce.search.service.SearchService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class SearchController {

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

    @Autowired
    private SearchService searchService;

    @Autowired
    private EmbeddingService embeddingService;

    @PostMapping("/search")
    public ResponseEntity<SearchResponse> semanticSearch(@Valid @RequestBody SearchRequest request) {
        try {
            logger.info("Performing semantic search for query: {}", request.getQuery());
            SearchResponse response = searchService.semanticSearch(request);
            logger.info("Search completed. Found {} results in {}ms",
                    response.getResults().size(), response.getExecutionTimeMs());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error performing search", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/search/title")
    public ResponseEntity<SearchResponse> searchByTitle(
            @RequestBody Map<String, Object> request) {
        try {
            String query = (String) request.get("query");
            int limit = (int) request.getOrDefault("limit", 10);
            int offset = (int) request.getOrDefault("offset", 0);
            double minSimilarity = (double) request.getOrDefault("minSimilarity", 0.0);

            logger.info("Performing title search for query: {}", query);
            SearchResponse response = searchService.searchByTitle(query, limit, offset, minSimilarity);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error performing title search", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/search/description")
    public ResponseEntity<SearchResponse> searchByDescription(
            @RequestBody Map<String, Object> request) {
        try {
            String query = (String) request.get("query");
            int limit = (int) request.getOrDefault("limit", 10);
            int offset = (int) request.getOrDefault("offset", 0);
            double minSimilarity = (double) request.getOrDefault("minSimilarity", 0.0);

            logger.info("Performing description search for query: {}", query);
            SearchResponse response = searchService.searchByDescription(query, limit, offset, minSimilarity);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error performing description search", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            Boolean embeddingServiceHealthy = embeddingService.checkHealth().block();

            Map<String, Object> health = Map.of(
                    "status", "healthy",
                    "timestamp", System.currentTimeMillis(),
                    "embeddingService", embeddingServiceHealthy != null ? embeddingServiceHealthy : false
            );

            return ResponseEntity.ok(health);
        } catch (Exception e) {
            logger.error("Health check failed", e);
            Map<String, Object> health = Map.of(
                    "status", "unhealthy",
                    "timestamp", System.currentTimeMillis(),
                    "error", e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, String>> root() {
        Map<String, String> info = Map.of(
                "service", "Semantic Search API",
                "version", "1.0.0",
                "status", "running"
        );
        return ResponseEntity.ok(info);
    }
}