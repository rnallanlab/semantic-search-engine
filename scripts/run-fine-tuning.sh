#!/bin/bash

set -e

echo "Starting model fine-tuning pipeline..."

# Configuration
FINE_TUNE_DIR="ml-model"
OUTPUT_MODEL_DIR="./fine_tuned_ecommerce_model"
BATCH_SIZE=${BATCH_SIZE:-32}
EPOCHS=${EPOCHS:-4}

# Check if database is accessible
if [ -z "$DATABASE_URL" ]; then
    echo "Error: DATABASE_URL environment variable is required"
    echo "Export it like: export DATABASE_URL='postgresql://user:pass@host:port/dbname'"
    exit 1
fi

echo "Using database: ${DATABASE_URL}"

# Navigate to ML model directory
cd $FINE_TUNE_DIR

# Install fine-tuning requirements
echo "Installing fine-tuning dependencies..."
pip install -r fine_tune_requirements.txt

# Check if we have enough products in database
echo "Checking database connectivity and data availability..."
python3 -c "
import psycopg2
import os
url = os.environ['DATABASE_URL']
# Parse postgresql://user:pass@host:port/dbname
url = url.replace('postgresql://', '')
parts = url.split('@')
user_pass = parts[0].split(':')
host_port_db = parts[1].split('/')
host_port = host_port_db[0].split(':')

conn = psycopg2.connect(
    host=host_port[0],
    port=int(host_port[1]) if len(host_port) > 1 else 5432,
    user=user_pass[0],
    password=user_pass[1],
    database=host_port_db[1]
)
cursor = conn.cursor()
cursor.execute('SELECT COUNT(*) FROM products WHERE title IS NOT NULL')
count = cursor.fetchone()[0]
print(f'Found {count} products with titles in database')
if count < 100:
    print('Warning: Less than 100 products found. Fine-tuning may not be effective.')
    exit(1)
cursor.close()
conn.close()
print('Database connectivity verified!')
"

if [ $? -ne 0 ]; then
    echo "Database check failed. Please ensure products are loaded."
    exit 1
fi

# Run fine-tuning
echo "Starting fine-tuning process..."
echo "  Batch size: $BATCH_SIZE"
echo "  Epochs: $EPOCHS"
echo "  Output directory: $OUTPUT_MODEL_DIR"

export BATCH_SIZE
export EPOCHS

python3 fine_tune.py

# Check if fine-tuning was successful
if [ ! -d "$OUTPUT_MODEL_DIR" ]; then
    echo "Error: Fine-tuning failed. Output model directory not found."
    exit 1
fi

# Validate the fine-tuned model
echo "Validating fine-tuned model..."
python3 -c "
from sentence_transformers import SentenceTransformer
import os

model_path = '$OUTPUT_MODEL_DIR'
if os.path.exists(model_path):
    model = SentenceTransformer(model_path)
    # Test encoding
    test_text = 'wireless bluetooth headphones'
    embedding = model.encode(test_text)
    print(f'Fine-tuned model loaded successfully!')
    print(f'Embedding dimension: {len(embedding)}')
    print(f'Test encoding shape: {embedding.shape}')
else:
    print('Error: Fine-tuned model directory not found')
    exit(1)
"

if [ $? -eq 0 ]; then
    echo "✅ Fine-tuning completed successfully!"
    echo ""
    echo "Results:"
    echo "  📁 Fine-tuned model: $OUTPUT_MODEL_DIR"
    echo "  📊 Results: fine_tuning_results.json"
    echo ""
    echo "Next steps:"
    echo "1. Review fine_tuning_results.json for improvement metrics"
    echo "2. Update ML model service to use fine-tuned model:"
    echo "   docker build -t your-ecr-repo/ml-model:fine-tuned ."
    echo "3. Deploy updated model to ECS"
    echo ""
    echo "To use the fine-tuned model in production:"
    echo "  Update MODEL_NAME environment variable to: $OUTPUT_MODEL_DIR"
else
    echo "❌ Model validation failed"
    exit 1
fi

cd ..