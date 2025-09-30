package com.ecommerce.search.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    private final WebClient webClient;

    @Value("${embedding.service.url}")
    private String embeddingServiceUrl;

    public EmbeddingService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public Mono<List<List<Double>>> generateEmbedding(String text) {
        EmbeddingRequest request = new EmbeddingRequest(text, true);

        return webClient.post()
                .uri(embeddingServiceUrl + "/embed")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
                .map(EmbeddingResponse::getEmbeddings);
    }

    public String formatEmbeddingForPostgres(List<Double> embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    public Mono<Boolean> checkHealth() {
        return webClient.get()
                .uri(embeddingServiceUrl + "/health")
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> "healthy".equals(response.get("status")))
                .onErrorReturn(false);
    }

    // DTOs
    public static class EmbeddingRequest {
        private String texts;
        private boolean normalize;

        public EmbeddingRequest() {}

        public EmbeddingRequest(String texts, boolean normalize) {
            this.texts = texts;
            this.normalize = normalize;
        }

        public String getTexts() { return texts; }
        public void setTexts(String texts) { this.texts = texts; }

        public boolean isNormalize() { return normalize; }
        public void setNormalize(boolean normalize) { this.normalize = normalize; }
    }

    public static class EmbeddingResponse {
        private List<List<Double>> embeddings;
        private String modelName;
        private int dimension;

        public EmbeddingResponse() {}

        public List<List<Double>> getEmbeddings() { return embeddings; }
        public void setEmbeddings(List<List<Double>> embeddings) { this.embeddings = embeddings; }

        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }

        public int getDimension() { return dimension; }
        public void setDimension(int dimension) { this.dimension = dimension; }
    }
}