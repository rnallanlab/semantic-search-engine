#!/bin/bash

set -e

# Configuration
AWS_REGION=${AWS_REGION:-us-east-1}
PROJECT_NAME=${PROJECT_NAME:-semantic-search}
ENVIRONMENT=${ENVIRONMENT:-prod}

echo "Setting up database for $PROJECT_NAME..."

# Get database connection details from Terraform outputs
cd infrastructure

if [ ! -f terraform.tfstate ]; then
    echo "Error: Terraform state file not found. Please run deployment first."
    exit 1
fi

DB_HOST=$(terraform output -raw rds_endpoint | cut -d: -f1)
DB_PORT=$(terraform output -raw rds_port)
DB_NAME="ecommerce_search"
DB_USER="postgres"

if [ -z "$DB_PASSWORD" ]; then
    echo "Error: DB_PASSWORD environment variable is required"
    exit 1
fi

cd ..

echo "Database Host: $DB_HOST"
echo "Database Port: $DB_PORT"
echo "Database Name: $DB_NAME"

# Create SQL script for database setup
cat > /tmp/setup_db.sql << EOF
-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create products table
CREATE TABLE IF NOT EXISTS products (
    asin VARCHAR(255) PRIMARY KEY,
    title TEXT,
    description TEXT,
    brand VARCHAR(255),
    category TEXT[],
    price DECIMAL(10,2),
    image_url TEXT,
    rating DECIMAL(3,2),
    review_count INTEGER,
    title_embedding vector(384),
    description_embedding vector(384),
    combined_embedding vector(384),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_products_title_embedding ON products USING ivfflat (title_embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_products_description_embedding ON products USING ivfflat (description_embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_products_combined_embedding ON products USING ivfflat (combined_embedding vector_cosine_ops);

-- Create indexes for filtering
CREATE INDEX IF NOT EXISTS idx_products_brand ON products (brand);
CREATE INDEX IF NOT EXISTS idx_products_price ON products (price);
CREATE INDEX IF NOT EXISTS idx_products_rating ON products (rating);
CREATE INDEX IF NOT EXISTS idx_products_category ON products USING GIN (category);

-- Create trigger for updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS \$\$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
\$\$ language 'plpgsql';

DROP TRIGGER IF EXISTS update_products_updated_at ON products;
CREATE TRIGGER update_products_updated_at
    BEFORE UPDATE ON products
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Grant permissions
GRANT ALL PRIVILEGES ON TABLE products TO postgres;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO postgres;

EOF

# Install PostgreSQL client if not present
if ! command -v psql &> /dev/null; then
    echo "Installing PostgreSQL client..."

    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        sudo apt-get update && sudo apt-get install -y postgresql-client
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        if command -v brew &> /dev/null; then
            brew install postgresql
        else
            echo "Please install PostgreSQL client manually"
            exit 1
        fi
    else
        echo "Please install PostgreSQL client manually"
        exit 1
    fi
fi

# Run database setup
echo "Setting up database schema..."
PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -f /tmp/setup_db.sql

echo "Database setup completed successfully!"

# Clean up
rm /tmp/setup_db.sql

echo ""
echo "You can now run the data ingestion pipeline:"
echo "cd data-pipeline"
echo "python data_ingestion.py"