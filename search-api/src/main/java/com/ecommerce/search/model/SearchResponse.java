package com.ecommerce.search.model;

import java.util.List;

public class SearchResponse {

    private List<ProductResult> results;
    private Integer totalCount;
    private Integer limit;
    private Integer offset;
    private String query;
    private Long executionTimeMs;

    public SearchResponse() {}

    public SearchResponse(List<ProductResult> results, Integer totalCount,
                         Integer limit, Integer offset, String query, Long executionTimeMs) {
        this.results = results;
        this.totalCount = totalCount;
        this.limit = limit;
        this.offset = offset;
        this.query = query;
        this.executionTimeMs = executionTimeMs;
    }

    // Getters and Setters
    public List<ProductResult> getResults() { return results; }
    public void setResults(List<ProductResult> results) { this.results = results; }

    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }

    public Integer getOffset() { return offset; }
    public void setOffset(Integer offset) { this.offset = offset; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public Long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(Long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public static class ProductResult {
        private String asin;
        private String title;
        private String description;
        private String brand;
        private List<String> category;
        private Double price;
        private String imageUrl;
        private Double rating;
        private Integer reviewCount;
        private Double similarity;

        public ProductResult() {}

        public ProductResult(Product product) {
            this.asin = product.getAsin();
            this.title = product.getTitle();
            this.description = product.getDescription();
            this.brand = product.getBrand();
            this.category = product.getCategory();
            this.price = product.getPrice();
            this.imageUrl = product.getImageUrl();
            this.rating = product.getRating();
            this.reviewCount = product.getReviewCount();
            this.similarity = product.getSimilarity();
        }

        // Getters and Setters
        public String getAsin() { return asin; }
        public void setAsin(String asin) { this.asin = asin; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getBrand() { return brand; }
        public void setBrand(String brand) { this.brand = brand; }

        public List<String> getCategory() { return category; }
        public void setCategory(List<String> category) { this.category = category; }

        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }

        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

        public Double getRating() { return rating; }
        public void setRating(Double rating) { this.rating = rating; }

        public Integer getReviewCount() { return reviewCount; }
        public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }

        public Double getSimilarity() { return similarity; }
        public void setSimilarity(Double similarity) { this.similarity = similarity; }
    }
}