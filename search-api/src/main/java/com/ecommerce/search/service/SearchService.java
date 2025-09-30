package com.ecommerce.search.service;

import com.ecommerce.search.model.Product;
import com.ecommerce.search.model.SearchRequest;
import com.ecommerce.search.model.SearchResponse;
import com.ecommerce.search.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EmbeddingService embeddingService;

    public SearchResponse semanticSearch(SearchRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // Generate embedding for the search query
            List<List<Double>> embeddings = embeddingService.generateEmbedding(request.getQuery()).block();

            if (embeddings == null || embeddings.isEmpty()) {
                throw new RuntimeException("Failed to generate embedding for query");
            }

            String queryEmbedding = embeddingService.formatEmbeddingForPostgres(embeddings.get(0));

            // Perform vector search
            List<Product> searchResults = productRepository.searchByVector(
                    queryEmbedding,
                    request.getMinSimilarity(),
                    request.getCategory(),
                    request.getBrand(),
                    request.getMinPrice(),
                    request.getMaxPrice(),
                    request.getMinRating(),
                    request.getLimit(),
                    request.getOffset()
            );

            // Get total count for pagination
            Long totalCount = productRepository.countSearchResults(
                    queryEmbedding,
                    request.getMinSimilarity(),
                    request.getCategory(),
                    request.getBrand(),
                    request.getMinPrice(),
                    request.getMaxPrice(),
                    request.getMinRating()
            );

            // Convert results to ProductResult objects
            List<SearchResponse.ProductResult> results = new ArrayList<>();
            for (Product product : searchResults) {
                results.add(new SearchResponse.ProductResult(product));
            }

            long executionTime = System.currentTimeMillis() - startTime;

            return new SearchResponse(
                    results,
                    totalCount.intValue(),
                    request.getLimit(),
                    request.getOffset(),
                    request.getQuery(),
                    executionTime
            );

        } catch (Exception e) {
            logger.error("Error performing semantic search", e);
            throw new RuntimeException("Search failed: " + e.getMessage(), e);
        }
    }

    public SearchResponse searchByTitle(String query, int limit, int offset, double minSimilarity) {
        long startTime = System.currentTimeMillis();

        try {
            List<List<Double>> embeddings = embeddingService.generateEmbedding(query).block();

            if (embeddings == null || embeddings.isEmpty()) {
                throw new RuntimeException("Failed to generate embedding for query");
            }

            String queryEmbedding = embeddingService.formatEmbeddingForPostgres(embeddings.get(0));

            List<Product> searchResults = productRepository.searchByTitleVector(
                    queryEmbedding, minSimilarity, limit, offset);

            List<SearchResponse.ProductResult> results = new ArrayList<>();
            for (Product product : searchResults) {
                results.add(new SearchResponse.ProductResult(product));
            }

            long executionTime = System.currentTimeMillis() - startTime;

            return new SearchResponse(
                    results,
                    results.size(),
                    limit,
                    offset,
                    query,
                    executionTime
            );

        } catch (Exception e) {
            logger.error("Error performing title search", e);
            throw new RuntimeException("Title search failed: " + e.getMessage(), e);
        }
    }

    public SearchResponse searchByDescription(String query, int limit, int offset, double minSimilarity) {
        long startTime = System.currentTimeMillis();

        try {
            List<List<Double>> embeddings = embeddingService.generateEmbedding(query).block();

            if (embeddings == null || embeddings.isEmpty()) {
                throw new RuntimeException("Failed to generate embedding for query");
            }

            String queryEmbedding = embeddingService.formatEmbeddingForPostgres(embeddings.get(0));

            List<Product> searchResults = productRepository.searchByDescriptionVector(
                    queryEmbedding, minSimilarity, limit, offset);

            List<SearchResponse.ProductResult> results = new ArrayList<>();
            for (Product product : searchResults) {
                results.add(new SearchResponse.ProductResult(product));
            }

            long executionTime = System.currentTimeMillis() - startTime;

            return new SearchResponse(
                    results,
                    results.size(),
                    limit,
                    offset,
                    query,
                    executionTime
            );

        } catch (Exception e) {
            logger.error("Error performing description search", e);
            throw new RuntimeException("Description search failed: " + e.getMessage(), e);
        }
    }

}