# Build Guide

This guide covers building all components of the semantic search engine.

## Prerequisites

- Java 21+ with Maven
- Python 3.9+
- Docker with Buildx support
- AWS CLI configured
- Access to AWS ECR repositories

## 1. Build Data Pipeline

The data pipeline is a Python application that doesn't require compilation, but you should verify dependencies:

```bash
cd data-pipeline

# Create virtual environment (optional but recommended)
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Verify installation
python -c "import sentence_transformers; print('Dependencies OK')"
```

### Run Data Ingestion

```bash
# Ensure environment variables are set
export DB_HOST=your-db-host
export DB_PORT=5432
export DB_NAME=ecommerce_search
export DB_USER=postgres
export DB_PASSWORD=your-password

# Or use .env file
source ../.env

# Run data ingestion
python data_ingestion.py
```

## 2. Build ML Model Service

### 2.1 Local Build and Test

```bash
cd ml-model

# Install dependencies
pip install -r requirements.txt

# Test locally
python app.py
# Service will start on http://localhost:8000

# Test in another terminal
curl -X POST "http://localhost:8000/embed" \
  -H "Content-Type: application/json" \
  -d '{"texts": "test query", "normalize": true}'
```

### 2.2 Build Docker Image

**Important**: Build for linux/amd64 architecture for AWS ECS:

```bash
cd ml-model

# Build for linux/amd64 (required for ECS)
docker buildx build \
  --platform linux/amd64 \
  --load \
  -t <AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/semantic-search-prod/ml-model:latest \
  .

# Verify the image
docker images | grep ml-model
```

### 2.3 Test Docker Image Locally

```bash
# Run the container
docker run -p 8000:8000 \
  <AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/semantic-search-prod/ml-model:latest

# Test the endpoint
curl -X POST "http://localhost:8000/embed" \
  -H "Content-Type: application/json" \
  -d '{"texts": "wireless headphones", "normalize": true}'

# Expected: JSON response with embedding array
```

## 3. Build Search API

### 3.1 Build JAR with Maven

```bash
cd search-api

# Set Java 21 (if multiple versions installed)
export JAVA_HOME=/path/to/java-21
# On macOS with Homebrew:
export JAVA_HOME=/opt/homebrew/opt/openjdk@21

# Clean and build
./mvnw clean package -DskipTests

# Verify build
ls -lh target/semantic-search-api-1.0.0.jar
```

Expected output: JAR file ~35MB

### 3.2 Run Tests

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=SearchServiceTest

# Run with coverage
./mvnw test jacoco:report
# View coverage: target/site/jacoco/index.html
```

### 3.3 Build Docker Image

**Important**: Build for linux/amd64 architecture for AWS ECS:

```bash
cd search-api

# Ensure JAR is built first
./mvnw clean package -DskipTests

# Build for linux/amd64 with no cache
docker buildx build \
  --platform linux/amd64 \
  --no-cache \
  --load \
  -t <AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/semantic-search-prod/search-api:latest \
  .

# Verify the image
docker images | grep search-api
```

### 3.4 Test Docker Image Locally

```bash
# Run the container
docker run -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://<DB_HOST>:5432/ecommerce_search \
  -e DATABASE_USERNAME=postgres \
  -e DATABASE_PASSWORD=<YOUR_PASSWORD> \
  -e EMBEDDING_SERVICE_URL=http://host.docker.internal:8000 \
  -e API_KEY=test-api-key \
  <AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/semantic-search-prod/search-api:latest

# Test the endpoint
curl -X POST "http://localhost:8080/api/v1/search" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: test-api-key" \
  -d '{"query": "laptop", "limit": 5}'
```

## 4. Push Images to AWS ECR

### 4.1 Set Up ECR Repositories

```bash
# Login to ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  <AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com

# Create repositories (if not already created by Terraform)
aws ecr create-repository \
  --repository-name semantic-search-prod/ml-model \
  --region us-east-1

aws ecr create-repository \
  --repository-name semantic-search-prod/search-api \
  --region us-east-1
```

### 4.2 Push ML Model Image

```bash
# Tag image (if needed)
docker tag ml-model:latest \
  <AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/semantic-search-prod/ml-model:latest

# Push to ECR
docker push <AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/semantic-search-prod/ml-model:latest

# Verify push
aws ecr describe-images \
  --repository-name semantic-search-prod/ml-model \
  --region us-east-1
```

### 4.3 Push Search API Image

```bash
# Tag image (if needed)
docker tag search-api:latest \
  <AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/semantic-search-prod/search-api:latest

# Push to ECR
docker push <AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/semantic-search-prod/search-api:latest

# Verify push
aws ecr describe-images \
  --repository-name semantic-search-prod/search-api \
  --region us-east-1
```

## 5. Build Scripts

We provide helper scripts for common build tasks:

### 5.1 Complete Build Script

Create `scripts/build-all.sh`:

```bash
#!/bin/bash
set -e

echo "=== Building Semantic Search Engine ==="

# Set AWS Account ID
export AWS_ACCOUNT_ID=${AWS_ACCOUNT_ID:-"<YOUR_ACCOUNT_ID>"}
export AWS_REGION=${AWS_REGION:-"us-east-1"}

# Build ML Model
echo "Building ML Model..."
cd ml-model
docker buildx build --platform linux/amd64 --load \
  -t $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/semantic-search-prod/ml-model:latest .
cd ..

# Build Search API
echo "Building Search API..."
cd search-api
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw clean package -DskipTests
docker buildx build --platform linux/amd64 --no-cache --load \
  -t $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/semantic-search-prod/search-api:latest .
cd ..

echo "=== Build Complete ==="
echo "ML Model: $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/semantic-search-prod/ml-model:latest"
echo "Search API: $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/semantic-search-prod/search-api:latest"
```

Make it executable:
```bash
chmod +x scripts/build-all.sh
```

Run it:
```bash
export AWS_ACCOUNT_ID=123456789012
./scripts/build-all.sh
```

## 6. Build Verification

### 6.1 Verify Docker Images

```bash
# List local images
docker images | grep semantic-search-prod

# Expected output:
# <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/semantic-search-prod/ml-model      latest
# <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/semantic-search-prod/search-api    latest
```

### 6.2 Verify ECR Images

```bash
# Check ML Model
aws ecr describe-images \
  --repository-name semantic-search-prod/ml-model \
  --region us-east-1 \
  --query 'imageDetails[0].{digest:imageDigest, pushed:imagePushedAt, tags:imageTags}'

# Check Search API
aws ecr describe-images \
  --repository-name semantic-search-prod/search-api \
  --region us-east-1 \
  --query 'imageDetails[0].{digest:imageDigest, pushed:imagePushedAt, tags:imageTags}'
```

### 6.3 Test Image Architecture

Verify images are built for correct platform:

```bash
# Check ML Model architecture
docker inspect <AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/semantic-search-prod/ml-model:latest \
  | jq '.[0].Architecture'
# Expected: "amd64"

# Check Search API architecture
docker inspect <AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/semantic-search-prod/search-api:latest \
  | jq '.[0].Architecture'
# Expected: "amd64"
```

## 7. Troubleshooting

### Build fails with "Java version mismatch"

**Solution**: Set correct JAVA_HOME
```bash
export JAVA_HOME=/path/to/java-21
java -version  # Should show "21.x.x"
```

### Docker build fails on Mac M1/M2

**Solution**: Ensure buildx is installed and use --platform flag
```bash
docker buildx create --use
docker buildx build --platform linux/amd64 ...
```

### Maven build fails with dependency errors

**Solution**: Clear Maven cache
```bash
rm -rf ~/.m2/repository
./mvnw clean install
```

### ECR push fails with authentication error

**Solution**: Re-authenticate with ECR
```bash
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  <AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com
```

## Next Steps

- [DEPLOYMENT.md](./DEPLOYMENT.md) - Deploy to AWS ECS
- [TESTING.md](./TESTING.md) - Run tests and validation
