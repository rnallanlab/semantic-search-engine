import os
from dotenv import load_dotenv

load_dotenv()

# Database Configuration
DATABASE_CONFIG = {
    'host': os.getenv('DB_HOST', 'localhost'),
    'port': os.getenv('DB_PORT', 5432),
    'database': os.getenv('DB_NAME', 'ecommerce_search'),
    'user': os.getenv('DB_USER', 'postgres'),
    'password': os.getenv('DB_PASSWORD', 'password')
}

# Model Configuration
MODEL_CONFIG = {
    'model_name': 'sentence-transformers/all-MiniLM-L6-v2',
    'embedding_dimension': 384,
    'batch_size': 32
}

# Data Configuration
DATA_CONFIG = {
    'dataset_url': 'https://raw.githubusercontent.com/luminati-io/eCommerce-dataset-samples/main/amazon-products.csv',
    'chunk_size': 1000,
    'max_records': 50000  # For prototype, limit dataset size
}

# AWS Configuration
AWS_CONFIG = {
    'region': os.getenv('AWS_REGION', 'us-east-1'),
    'model_endpoint': os.getenv('MODEL_ENDPOINT', ''),
    's3_bucket': os.getenv('S3_BUCKET', 'semantic-search-data')
}