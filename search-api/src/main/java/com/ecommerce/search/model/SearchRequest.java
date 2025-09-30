package com.ecommerce.search.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public class SearchRequest {

    @NotBlank(message = "Query cannot be empty")
    private String query;

    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 100, message = "Limit cannot exceed 100")
    private Integer limit = 10;

    @Min(value = 0, message = "Offset cannot be negative")
    private Integer offset = 0;

    private Double minSimilarity = 0.0;

    private String category;
    private String brand;
    private Double minPrice;
    private Double maxPrice;
    private Double minRating;

    public SearchRequest() {}

    public SearchRequest(String query) {
        this.query = query;
    }

    // Getters and Setters
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }

    public Integer getOffset() { return offset; }
    public void setOffset(Integer offset) { this.offset = offset; }

    public Double getMinSimilarity() { return minSimilarity; }
    public void setMinSimilarity(Double minSimilarity) { this.minSimilarity = minSimilarity; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public Double getMinPrice() { return minPrice; }
    public void setMinPrice(Double minPrice) { this.minPrice = minPrice; }

    public Double getMaxPrice() { return maxPrice; }
    public void setMaxPrice(Double maxPrice) { this.maxPrice = maxPrice; }

    public Double getMinRating() { return minRating; }
    public void setMinRating(Double minRating) { this.minRating = minRating; }
}