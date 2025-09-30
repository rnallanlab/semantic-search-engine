-- H2 test schema for Search API
-- Note: H2 doesn't support vector types, so we'll use VARCHAR for embeddings

CREATE TABLE IF NOT EXISTS products (
    asin VARCHAR(20) PRIMARY KEY,
    title TEXT,
    description TEXT,
    brand VARCHAR(255),
    category VARCHAR(1000), -- JSON array as string in H2
    price DECIMAL(10,2),
    image_url VARCHAR(500),
    rating DECIMAL(3,2),
    review_count INTEGER,
    title_embedding TEXT, -- Vector as JSON string
    description_embedding TEXT, -- Vector as JSON string
    combined_embedding TEXT, -- Vector as JSON string
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index for faster searches
CREATE INDEX IF NOT EXISTS idx_products_brand ON products(brand);
CREATE INDEX IF NOT EXISTS idx_products_price ON products(price);
CREATE INDEX IF NOT EXISTS idx_products_rating ON products(rating);