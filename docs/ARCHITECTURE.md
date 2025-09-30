# Semantic Search Engine Design Document

## 1. System Overview

This document describes the design and architecture of a production-ready semantic search engine for e-commerce applications, built to run on AWS infrastructure. The system provides vector-based similarity search capabilities with traditional filtering options, designed for scalability and high availability.

### 1.1 Problem Statement

Traditional keyword-based search in e-commerce has limitations:
- Exact keyword matching requirements
- Poor handling of synonyms and variations
- Limited understanding of user intent
- Difficulty with natural language queries

Our semantic search solution addresses these by:
- Understanding semantic meaning of queries
- Matching intent rather than exact keywords
- Supporting natural language search
- Providing relevance-based ranking

### 1.2 Solution Architecture

The system follows a microservices architecture with clear separation of concerns:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Data Pipeline │    │   ML Model      │    │   Search API    │
│                 │    │   Service       │    │                 │
│ • Ingestion     │    │ • Embeddings    │    │ • REST API      │
│ • Vector Gen    │    │ • HuggingFace   │    │ • Auth          │
│ • Storage       │    │ • FastAPI       │    │ • Spring Boot   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │   PostgreSQL    │
                    │   + pgvector    │
                    │                 │
                    │ • Product Data  │
                    │ • Vector Store  │
                    │ • Metadata      │
                    └─────────────────┘
```

## 2. Component Design

### 2.1 Data Engineering Pipeline

**Purpose**: Ingest, process, and vectorize e-commerce product data.

**Technologies**: Python, sentence-transformers, PostgreSQL, pgvector

**Key Components**:

```python
class DataIngestionPipeline:
    - download_dataset()     # Fetch product data
    - parse_product_data()    # Clean and structure data
    - generate_embeddings()  # Create vector representations
    - insert_products()      # Store in database
```

**Data Flow**:
1. **Raw Data Ingestion**: Downloads product dataset (JSON format)
2. **Data Cleaning**: Extracts relevant fields (title, description, brand, etc.)
3. **Vector Generation**: Creates embeddings for:
   - Product titles
   - Product descriptions
   - Combined text (title + description + brand)
4. **Database Storage**: Inserts structured data with vector columns

**Design Decisions**:
- **Multiple Embedding Types**: Separate vectors for title, description, and combined text allow for targeted search strategies
- **Batch Processing**: Embeddings generated in configurable batches for memory efficiency
- **Incremental Updates**: ON CONFLICT handling for data updates
- **Data Validation**: Filters out products without essential information

### 2.2 ML Model Service

**Purpose**: Provide embedding generation as a service for real-time search queries.

**Technologies**: FastAPI, HuggingFace Transformers, sentence-transformers

**Architecture**:

```python
@app.post("/embed")
async def generate_embeddings(request: EmbeddingRequest):
    # Generate vector embeddings for input text
    # Returns normalized vectors for similarity search
```

**Key Features**:
- **Model**: sentence-transformers/all-MiniLM-L6-v2 (384-dimensional vectors)
- **Normalization**: L2 normalized vectors for cosine similarity
- **Health Checks**: Endpoint monitoring for deployment
- **Containerized**: Docker container for ECS Fargate deployment

**Design Decisions**:
- **Model Choice**: MiniLM-L6-v2 balances performance and accuracy for e-commerce search
- **Stateless Service**: No persistent state, allowing horizontal scaling
- **Async Processing**: FastAPI async support for concurrent requests
- **Standardized API**: REST API for easy integration

### 2.3 Search API

**Purpose**: Provide semantic search capabilities with filtering and ranking.

**Technologies**: Java Spring Boot, Spring Data JPA, PostgreSQL JDBC

**Core Components**:

```java
@RestController
public class SearchController {
    @PostMapping("/api/v1/search")
    public SearchResponse semanticSearch(@RequestBody SearchRequest request)

    @PostMapping("/api/v1/search/title")
    public SearchResponse searchByTitle(@RequestBody Map<String, Object> request)

    @PostMapping("/api/v1/search/description")
    public SearchResponse searchByDescription(@RequestBody Map<String, Object> request)
}
```

**Search Algorithm**:
1. **Query Processing**: Generate embedding for search query
2. **Vector Search**: Use PostgreSQL cosine distance operator (`<=>`)
3. **Filtering**: Apply traditional filters (price, brand, category, rating)
4. **Ranking**: Sort by similarity score (1 - cosine_distance)
5. **Pagination**: Support for offset/limit pagination

**API Design**:
```json
{
  "query": "wireless bluetooth headphones",
  "limit": 10,
  "offset": 0,
  "minSimilarity": 0.1,
  "category": "Electronics",
  "brand": "Sony",
  "minPrice": 50.0,
  "maxPrice": 200.0,
  "minRating": 4.0
}
```

**Security**:
- **API Key Authentication**: Custom filter for x-api-key header validation
- **Input Validation**: Bean validation for request parameters
- **Rate Limiting**: Can be implemented at ALB level

### 2.4 Database Design

**Technology**: PostgreSQL 15 with pgvector extension

**Schema Design**:

```sql
CREATE TABLE products (
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
```

**Indexing Strategy**:
```sql
-- Vector similarity indexes (IVFFlat for approximate search)
CREATE INDEX idx_products_combined_embedding
ON products USING ivfflat (combined_embedding vector_cosine_ops);

-- Traditional indexes for filtering
CREATE INDEX idx_products_brand ON products (brand);
CREATE INDEX idx_products_price ON products (price);
CREATE INDEX idx_products_rating ON products (rating);
CREATE INDEX idx_products_category ON products USING GIN (category);
```

**Design Decisions**:
- **Vector Dimensions**: 384-dimensional vectors from MiniLM-L6-v2 model
- **Index Type**: IVFFlat for good balance of speed and accuracy
- **Hybrid Approach**: Combines vector search with traditional SQL filtering
- **Partitioning**: Can be added for large datasets by category or brand

## 3. Infrastructure Design

### 3.1 AWS Architecture

```
Internet
    │
┌───▼────┐
│  ALB   │ ── Public endpoints, SSL termination
└───┬────┘
    │
┌───▼────────────────────┐
│     ECS Fargate        │
│  ┌─────────────────┐   │
│  │   Search API    │   │ ── 2 instances, auto-scaling
│  │   (Port 8080)   │   │
│  └─────────────────┘   │
│  ┌─────────────────┐   │
│  │   ML Model      │   │ ── 1 instance, can scale
│  │   (Port 8000)   │   │
│  └─────────────────┘   │
└────────────────────────┘
    │
┌───▼─────────────────────┐
│   RDS PostgreSQL       │ ── Private subnet, encrypted
│   + pgvector            │
└─────────────────────────┘
```

**Network Design**:
- **VPC**: Isolated network environment
- **Public Subnets**: ALB and NAT Gateway
- **Private Subnets**: ECS tasks and RDS
- **Security Groups**: Fine-grained access control

**Scaling Strategy**:
- **Horizontal Scaling**: ECS service auto-scaling based on CPU/memory
- **Database**: Read replicas for heavy read workloads
- **Caching**: Can add ElastiCache for frequent queries

### 3.2 Container Design

**Search API Container**:
```dockerfile
FROM openjdk:21-jdk-slim
# Multi-stage build for smaller image
# Health checks for container orchestration
# Non-root user for security
```

**ML Model Container**:
```dockerfile
FROM python:3.9-slim
# Pre-downloaded model weights to reduce startup time
# Optimized Python dependencies
# Health checks and monitoring
```

**Design Considerations**:
- **Image Size**: Optimized for faster deployments
- **Security**: Non-root users, minimal attack surface
- **Monitoring**: Built-in health checks
- **Immutability**: Stateless containers for easy scaling

## 4. Performance Optimizations

### 4.1 Database Optimizations

**Vector Search Performance**:
- **IVFFlat Index**: Approximate nearest neighbor search
- **Index Parameters**: Tuned for dataset size and query patterns
- **Connection Pooling**: HikariCP for efficient connection management

**Query Optimization**:
```sql
-- Optimized query with early filtering
SELECT p.*, (1 - (p.combined_embedding <=> $1)) AS similarity
FROM products p
WHERE (1 - (p.combined_embedding <=> $1)) >= $2
  AND ($3 IS NULL OR $3 = ANY(p.category))
  AND ($4 IS NULL OR p.price BETWEEN $5 AND $6)
ORDER BY similarity DESC
LIMIT $7 OFFSET $8;
```

### 4.2 Application Performance

**Caching Strategy**:
- **Embedding Cache**: Cache embeddings for common queries
- **Result Cache**: Cache search results for popular queries
- **Database Cache**: Query result caching at application level

**Async Processing**:
- **Reactive Streams**: WebFlux for non-blocking I/O
- **Concurrent Processing**: Parallel embedding generation
- **Circuit Breakers**: Fault tolerance for service calls

### 4.3 Infrastructure Performance

**Auto-scaling Configuration**:
```json
{
  "targetValue": 70,
  "scaleUpCooldown": 300,
  "scaleDownCooldown": 600,
  "metric": "CPUUtilization"
}
```

**Load Balancing**:
- **Health Checks**: Application-aware health monitoring
- **Sticky Sessions**: Not required due to stateless design
- **Traffic Distribution**: Round-robin with health-based routing

## 5. Security Design

### 5.1 Authentication & Authorization

**API Key Authentication**:
```java
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    // Validates x-api-key header
    // Sets security context for request
    // Denies access for invalid/missing keys
}
```

**Security Headers**:
- **API Key**: Required for all search endpoints
- **CORS**: Configurable for frontend integration
- **HTTPS**: Enforced at ALB level (can be added)

### 5.2 Network Security

**Security Groups**:
- **ALB**: Inbound 80/443 from internet
- **ECS**: Inbound 8080/8000 from ALB only
- **RDS**: Inbound 5432 from ECS only

**Data Encryption**:
- **At Rest**: RDS encryption enabled
- **In Transit**: TLS for all connections
- **Secrets**: AWS Secrets Manager for sensitive data

### 5.3 Application Security

**Input Validation**:
```java
public class SearchRequest {
    @NotBlank(message = "Query cannot be empty")
    private String query;

    @Min(value = 1) @Max(value = 100)
    private Integer limit = 10;
}
```

**Error Handling**:
- **Sanitized Responses**: No stack traces in production
- **Rate Limiting**: Can be implemented at ALB level
- **SQL Injection Prevention**: Parameterized queries

## 6. Monitoring & Observability

### 6.1 Logging Strategy

**Structured Logging**:
```java
@Slf4j
public class SearchService {
    public SearchResponse semanticSearch(SearchRequest request) {
        logger.info("Performing search: query={}, filters={}",
                   request.getQuery(), request.getFilters());
        // ... processing ...
        logger.info("Search completed: results={}, time={}ms",
                   results.size(), executionTime);
    }
}
```

**Log Aggregation**:
- **CloudWatch Logs**: Centralized log collection
- **Log Groups**: Separate for each service
- **Retention**: Configurable retention periods

### 6.2 Metrics & Monitoring

**Application Metrics**:
- **Search Latency**: P50, P95, P99 response times
- **Error Rates**: 4xx/5xx error percentages
- **Throughput**: Requests per second
- **Cache Hit Rates**: For performance optimization

**Infrastructure Metrics**:
- **CPU/Memory Utilization**: For auto-scaling
- **Database Performance**: Query times, connection pools
- **Network**: Request/response sizes, network latency

### 6.3 Health Checks

**Application Health**:
```java
@GetMapping("/health")
public ResponseEntity<Map<String, Object>> healthCheck() {
    // Check database connectivity
    // Check embedding service availability
    // Return comprehensive health status
}
```

**Infrastructure Health**:
- **ALB Health Checks**: HTTP-based application health
- **ECS Health Checks**: Container-level monitoring
- **RDS Monitoring**: Database performance metrics

## 7. Deployment Strategy

### 7.1 Infrastructure as Code

**Terraform Structure**:
```
infrastructure/
├── main.tf          # Core infrastructure
├── ecs.tf           # Container orchestration
├── alb.tf           # Load balancing
├── variables.tf     # Configuration
└── outputs.tf       # Resource outputs
```

**Environment Management**:
- **Variables**: Environment-specific configuration
- **State Management**: Remote state for team collaboration
- **Module Structure**: Reusable components

### 7.2 CI/CD Pipeline

**Deployment Process**:
1. **Code Commit**: Trigger build pipeline
2. **Image Build**: Docker images for each service
3. **Image Push**: Upload to ECR repositories
4. **Service Update**: Rolling deployment to ECS
5. **Health Validation**: Verify deployment success

**Rollback Strategy**:
- **Blue/Green Deployment**: Zero-downtime deployments
- **Health Monitoring**: Automatic rollback on failures
- **Database Migrations**: Backward-compatible changes

## 8. Future Enhancements

### 8.1 Advanced Features

**Model Fine-tuning**:
- **Domain-specific Models**: Train on e-commerce data
- **Multi-modal Search**: Image + text search capabilities
- **Personalization**: User-specific search results

**Search Improvements**:
- **Query Understanding**: Intent classification
- **Auto-complete**: Real-time search suggestions
- **Analytics**: Search behavior tracking

### 8.2 Scalability Improvements

**Performance Optimizations**:
- **GPU Acceleration**: For embedding generation
- **Distributed Search**: Sharded vector databases
- **CDN Integration**: Global content delivery

**Operational Improvements**:
- **A/B Testing**: Search algorithm comparisons
- **Feature Flags**: Runtime configuration changes
- **Cost Optimization**: Reserved instances, spot instances

## 9. Cost Analysis

### 9.1 Infrastructure Costs (Monthly)

**Development Environment**:
- **RDS db.t3.micro**: ~$20
- **ECS Fargate**: ~$30 (2 vCPU, 4GB RAM)
- **ALB**: ~$20
- **ECR**: ~$5
- **Total**: ~$75/month

**Production Environment**:
- **RDS db.r5.large**: ~$150
- **ECS Fargate**: ~$200 (auto-scaling)
- **ALB**: ~$25
- **CloudWatch**: ~$15
- **Total**: ~$390/month

### 9.2 Cost Optimization

**Strategies**:
- **Reserved Instances**: 30-60% savings for predictable workloads
- **Spot Instances**: For batch processing tasks
- **Auto-scaling**: Right-size resources based on demand
- **Data Lifecycle**: Archive old search logs

## 10. Conclusion

This semantic search engine design provides a robust, scalable solution for e-commerce search requirements. The architecture balances performance, security, and maintainability while leveraging modern cloud-native technologies and best practices.

**Key Strengths**:
- **Microservices Architecture**: Clear separation of concerns
- **Vector Search**: State-of-the-art semantic understanding
- **Cloud-native**: Built for AWS with auto-scaling
- **Production-ready**: Security, monitoring, and deployment automation

**Success Metrics**:
- **Search Relevance**: Improved user satisfaction
- **Performance**: Sub-100ms search response times
- **Availability**: 99.9% uptime SLA
- **Scalability**: Handle 1000+ concurrent users

The system is designed to evolve with changing requirements and can be extended with additional features as needed.