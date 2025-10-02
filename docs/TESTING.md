# Testing Guide

This guide covers all testing strategies for the semantic search engine, from unit tests to end-to-end API testing.

## Testing Strategy

```
Unit Tests → Integration Tests → API Tests → Performance Tests
```

## 1. Unit Tests

### 1.1 Search API Unit Tests

**Run all tests:**
```bash
cd search-api
./mvnw test
```

**Run specific test class:**
```bash
./mvnw test -Dtest=SearchServiceTest
./mvnw test -Dtest=ProductRepositoryTest
```

**Run with coverage report:**
```bash
./mvnw clean test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

**Key test classes:**
- `SearchServiceTest` - Tests search business logic
- `ProductRepositoryTest` - Tests database queries
- `EmbeddingServiceTest` - Tests ML model integration

### 1.2 Data Pipeline Unit Tests

```bash
cd data-pipeline

# Run all tests
pytest tests/

# Run with coverage
pytest --cov=. tests/

# Run specific test
pytest tests/test_data_ingestion.py -v
```

## 2. Integration Tests

### 2.1 Search API Integration Tests

Integration tests verify the complete flow from API to database:

```bash
cd search-api

# Run integration tests
./mvnw test -Dtest=*IntegrationTest

# Specific integration test
./mvnw test -Dtest=SearchApiIntegrationTest
```

**Test scenarios:**
- Query processing with real embeddings
- Database vector search
- Filtering and pagination
- Error handling

### 2.2 Database Integration Tests

Test database connectivity and vector search:

```bash
# Run pure JDBC search test
./mvnw test -Dtest=PureJdbcSearchTest

# This tests:
# - PostgreSQL connectivity
# - pgvector extension
# - Vector similarity queries
# - Index performance
```

## 3. API Functional Tests

### 3.1 Health Check

```bash
# Test health endpoint (no API key required)
curl -s http://<ALB_URL>/api/v1/health | jq .

# Expected response:
{
  "timestamp": 1696089600000,
  "status": "healthy",
  "embeddingService": true
}
```

### 3.2 Basic Semantic Search

```bash
# Search for products
curl -X POST "http://<ALB_URL>/api/v1/search" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <YOUR_API_KEY>" \
  -d '{
    "query": "wireless bluetooth headphones",
    "limit": 5
  }' | jq .

# Expected response includes:
# - results array with products
# - totalCount
# - executionTimeMs
# - similarity scores
```

### 3.3 Search with Filters

```bash
# Search with price and rating filters
curl -X POST "http://<ALB_URL>/api/v1/search" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <YOUR_API_KEY>" \
  -d '{
    "query": "laptop",
    "minPrice": 10,
    "maxPrice": 100,
    "minRating": 4.0,
    "limit": 10
  }' | jq .
```

### 3.4 Title-Only Search

```bash
# Search only in product titles
curl -X POST "http://<ALB_URL>/api/v1/search/title" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <YOUR_API_KEY>" \
  -d '{
    "query": "gaming mouse",
    "limit": 5
  }' | jq .
```

### 3.5 Description-Only Search

```bash
# Search only in product descriptions
curl -X POST "http://<ALB_URL>/api/v1/search/description" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <YOUR_API_KEY>" \
  -d '{
    "query": "waterproof bluetooth speaker",
    "limit": 5
  }' | jq .
```

### 3.6 Error Handling Tests

**Missing API Key:**
```bash
curl -X POST "http://<ALB_URL>/api/v1/search" \
  -H "Content-Type: application/json" \
  -d '{"query": "test"}'

# Expected: 401 Unauthorized
```

**Invalid API Key:**
```bash
curl -X POST "http://<ALB_URL>/api/v1/search" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: invalid-key" \
  -d '{"query": "test"}'

# Expected: 401 Unauthorized
```

**Empty Query:**
```bash
curl -X POST "http://<ALB_URL>/api/v1/search" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <YOUR_API_KEY>" \
  -d '{"query": "", "limit": 5}'

# Expected: 400 Bad Request with validation error
```

## 4. ML Model Service Tests

### 4.1 Embedding Generation

```bash
# Test ML model endpoint
curl -X POST "http://<ALB_URL>/embed" \
  -H "Content-Type: application/json" \
  -d '{
    "texts": "wireless headphones",
    "normalize": true
  }' | jq .

# Expected response:
{
  "embeddings": [[0.123, -0.456, ...]]  # 384-dimensional vector
}
```

### 4.2 Batch Embedding

```bash
# Test batch embedding generation
curl -X POST "http://<ALB_URL>/embed" \
  -H "Content-Type: application/json" \
  -d '{
    "texts": ["laptop", "headphones", "mouse"],
    "normalize": true
  }' | jq .

# Expected: Array of 3 embeddings
```

### 4.3 ML Model Health

```bash
# Test ML model health
curl -s http://<ALB_URL>/health | jq .

# Expected response:
{
  "status": "healthy",
  "model": "sentence-transformers/all-MiniLM-L6-v2",
  "embedding_dim": 384
}
```

## 5. Performance Tests

### 5.1 Search Latency

Test search response times:

```bash
# Measure latency
for i in {1..10}; do
  curl -w "@curl-format.txt" -o /dev/null -s \
    -X POST "http://<ALB_URL>/api/v1/search" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: <YOUR_API_KEY>" \
    -d '{"query": "wireless headphones", "limit": 10}'
done
```

Create `curl-format.txt`:
```
time_total: %{time_total}s\n
```

**Target Performance:**
- P50 latency: < 500ms
- P95 latency: < 1000ms
- P99 latency: < 2000ms

### 5.2 Concurrent Load Test

Use Apache Bench or similar tool:

```bash
# Install ab (Apache Bench)
# macOS: brew install httpd
# Ubuntu: sudo apt install apache2-utils

# Run load test (100 requests, 10 concurrent)
ab -n 100 -c 10 \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <YOUR_API_KEY>" \
  -p search-request.json \
  http://<ALB_URL>/api/v1/search

# Create search-request.json:
{"query": "laptop", "limit": 10}
```

**Expected Results:**
- Requests per second: > 50
- Failed requests: 0
- Mean response time: < 500ms

### 5.3 Database Performance

Test vector search performance:

```sql
-- Connect to database
psql -h <DB_HOST> -U postgres -d ecommerce_search

-- Test vector search speed
EXPLAIN ANALYZE
SELECT p.*, (1 - (p.combined_embedding <=> '[0.1, 0.2, ...]'::vector)) AS similarity
FROM products p
WHERE (1 - (p.combined_embedding <=> '[0.1, 0.2, ...]'::vector)) >= 0.1
ORDER BY similarity DESC
LIMIT 10;

-- Expected: < 100ms execution time with index
```

## 6. Test Automation

### 6.1 CI/CD Test Script

Create `scripts/run-tests.sh`:

```bash
#!/bin/bash
set -e

echo "=== Running All Tests ==="

# Unit tests
echo "Running Search API unit tests..."
cd search-api
./mvnw clean test
cd ..

# Integration tests
echo "Running integration tests..."
cd search-api
./mvnw test -Dtest=*IntegrationTest
cd ..

# Python tests (if any)
echo "Running data pipeline tests..."
cd data-pipeline
pytest tests/ -v
cd ..

echo "=== All Tests Passed ==="
```

### 6.2 Smoke Test Script

Create `scripts/smoke-test.sh`:

```bash
#!/bin/bash
set -e

API_URL=${API_URL:-"http://<ALB_URL>"}
API_KEY=${API_KEY:-"<YOUR_API_KEY>"}

echo "=== Running Smoke Tests ==="

# Test 1: Health check
echo "Test 1: Health check..."
curl -sf "$API_URL/api/v1/health" > /dev/null || exit 1

# Test 2: Basic search
echo "Test 2: Basic search..."
curl -sf -X POST "$API_URL/api/v1/search" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d '{"query": "test", "limit": 1}' > /dev/null || exit 1

# Test 3: ML model
echo "Test 3: ML model embedding..."
curl -sf -X POST "$API_URL/embed" \
  -H "Content-Type: application/json" \
  -d '{"texts": "test", "normalize": true}' > /dev/null || exit 1

echo "=== All Smoke Tests Passed ==="
```

## 7. Test Data

### 7.1 Sample Test Queries

Create `tests/test-queries.json`:

```json
{
  "queries": [
    {"query": "wireless bluetooth headphones", "expected_min_results": 50},
    {"query": "gaming laptop", "expected_min_results": 30},
    {"query": "smartphone", "expected_min_results": 100},
    {"query": "4k monitor", "expected_min_results": 20},
    {"query": "mechanical keyboard", "expected_min_results": 25}
  ]
}
```

### 7.2 Automated Query Testing

```bash
# Test all queries
cat tests/test-queries.json | jq -r '.queries[] | @json' | while read query; do
  SEARCH_QUERY=$(echo $query | jq -r '.query')
  echo "Testing: $SEARCH_QUERY"

  curl -s -X POST "http://<ALB_URL>/api/v1/search" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: <YOUR_API_KEY>" \
    -d "{\"query\": \"$SEARCH_QUERY\", \"limit\": 10}" | jq '.totalCount'
done
```

## 8. Test Coverage

### 8.1 Generate Coverage Report

```bash
cd search-api
./mvnw clean test jacoco:report

# View report
open target/site/jacoco/index.html
```

**Coverage Targets:**
- Line coverage: > 80%
- Branch coverage: > 70%
- Method coverage: > 85%

### 8.2 Coverage by Component

```bash
# Generate detailed coverage
./mvnw jacoco:report

# View coverage by package:
# - controller: > 90%
# - service: > 85%
# - repository: > 80%
```

## 9. Validation Checklist

Before deploying to production:

- [ ] All unit tests pass
- [ ] Integration tests pass
- [ ] API functional tests pass
- [ ] Performance tests meet targets
- [ ] Health checks return healthy status
- [ ] Error handling works correctly
- [ ] Security tests pass (API key validation)
- [ ] Load test completes successfully
- [ ] Database queries use indexes
- [ ] No memory leaks in load testing

## Next Steps

- [DEPLOYMENT.md](./DEPLOYMENT.md) - Deploy to production
- [ARCHITECTURE.md](./ARCHITECTURE.md) - Understand system design
