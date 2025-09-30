package com.ecommerce.search.model;

import java.time.LocalDateTime;
import java.util.List;

public class Product {

    private String asin;
    private String title;
    private String description;
    private String brand;
    private List<String> category;
    private Double price;
    private String imageUrl;
    private Double rating;
    private Integer reviewCount;
    private String titleEmbedding;
    private String descriptionEmbedding;
    private String combinedEmbedding;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Double similarity;

    public Product() {}

    public Product(String asin, String title, String description, String brand,
                   List<String> category, Double price, String imageUrl,
                   Double rating, Integer reviewCount) {
        this.asin = asin;
        this.title = title;
        this.description = description;
        this.brand = brand;
        this.category = category;
        this.price = price;
        this.imageUrl = imageUrl;
        this.rating = rating;
        this.reviewCount = reviewCount;
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

    public String getTitleEmbedding() { return titleEmbedding; }
    public void setTitleEmbedding(String titleEmbedding) { this.titleEmbedding = titleEmbedding; }

    public String getDescriptionEmbedding() { return descriptionEmbedding; }
    public void setDescriptionEmbedding(String descriptionEmbedding) { this.descriptionEmbedding = descriptionEmbedding; }

    public String getCombinedEmbedding() { return combinedEmbedding; }
    public void setCombinedEmbedding(String combinedEmbedding) { this.combinedEmbedding = combinedEmbedding; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Double getSimilarity() { return similarity; }
    public void setSimilarity(Double similarity) { this.similarity = similarity; }
}