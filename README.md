# E-commerce Semantic Search Engine

A production-ready semantic search solution for e-commerce applications, built with modern NLP techniques and deployed on AWS infrastructure.

## Overview

This project implements a complete semantic search engine that understands the meaning behind search queries, not just keywords. Perfect for e-commerce platforms looking to improve product discovery and user experience.

**Key Features:**
- **Semantic Understanding** - Natural language query processing
- **Fast Vector Search** - Sub-second response times with pgvector
- **Hybrid Filtering** - Combine semantic search with traditional filters
- **Production Ready** - Containerized, scalable AWS deployment
- **Secure** - API key authentication and AWS security best practices

## Table of Contents

- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [Documentation](#documentation)
- [Project Structure](#project-structure)
- [Tech Stack](#tech-stack)
- [API Examples](#api-examples)
- [Contributing](#contributing)

## Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Data Pipeline  │    │   ML Model      │◀───│   Search API    │
│  (Python)       │    │   (FastAPI)     │    │   (Spring Boot) │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                      │                       │
         └──────────────────────┼───────────────────────┘
                                │
                   ┌────────────▼──────────────┐
                   │   PostgreSQL + pgvector   │
                   │   (Vector Database)       │
                   └───────────────────────────┘
```

**Components:**
1. **Data Pipeline** - Ingests product data and generates embeddings
2. **ML Model Service** - Generates vector embeddings using HuggingFace transformers
3. **Search API** - REST API for semantic search with filtering
4. **Vector Database** - PostgreSQL with pgvector extension for similarity search

For detailed architecture, see [ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Quick Start

### Prerequisites

- **Java 21+**
- **Python 3.9+**
- **Docker** & Docker Buildx
- **PostgreSQL 15+** with pgvector
- **AWS CLI** (for cloud deployment)
- **Terraform** (for infrastructure)

### Local Development

1. **Clone the repository:**
   ```bash
   git clone https://github.com/<your-org>/semantic-search-engine.git
   cd semantic-search-engine
   ```

2. **Set up environment:**
   ```bash
   # Copy example environment file
   cp data-pipeline/.env.example data-pipeline/.env

   # Edit .env with your database credentials
   vi data-pipeline/.env
   ```

3. **Start services:**
   ```bash
   # Start ML model service
   cd ml-model
   pip install -r requirements.txt
   python app.py

   # In another terminal, start Search API
   cd search-api
   export DATABASE_PASSWORD=your-password
   export API_KEY=your-api-key
   ./mvnw spring-boot:run
   ```

4. **Load sample data:**
   ```bash
   cd data-pipeline
   python data_ingestion.py
   ```

5. **Test the API:**
   ```bash
   curl -X POST "http://localhost:8080/api/v1/search" \
     -H "Content-Type: application/json" \
     -H "X-API-Key: your-api-key" \
     -d '{"query": "wireless headphones", "limit": 5}'
   ```

## Documentation

Comprehensive guides for setup, building, deployment, and testing:

- **[SETUP.md](docs/SETUP.md)** - Local development environment setup
- **[BUILD.md](docs/BUILD.md)** - Building Docker images and artifacts
- **[DEPLOYMENT.md](docs/DEPLOYMENT.md)** - Deploying to AWS with Terraform
- **[TESTING.md](docs/TESTING.md)** - Running unit, integration, and API tests
- **[ARCHITECTURE.md](docs/ARCHITECTURE.md)** - System design and technical details

## Project Structure

```
semantic-search-engine/
├── data-pipeline/          # Data ingestion and vectorization
│   ├── data_ingestion.py   # Main ingestion script
│   ├── config.py           # Database configuration
│   └── requirements.txt    # Python dependencies
├── ml-model/               # ML embedding service
│   ├── app.py              # FastAPI application
│   ├── Dockerfile          # Container definition
│   └── requirements.txt    # Python dependencies
├── search-api/             # REST API service
│   ├── src/main/java/      # Java source code
│   ├── pom.xml             # Maven configuration
│   └── Dockerfile          # Container definition
├── infrastructure/         # Terraform IaC
│   ├── main.tf             # Main infrastructure
│   ├── ecs.tf              # ECS configuration
│   ├── alb.tf              # Load balancer
│   └── variables.tf        # Configuration variables
├── scripts/                # Helper scripts
│   ├── deploy.sh           # Deployment automation
│   └── setup-database.sh   # Database initialization
└── docs/                   # Documentation
    ├── SETUP.md
    ├── BUILD.md
    ├── DEPLOYMENT.md
    ├── TESTING.md
    └── ARCHITECTURE.md
```

## Tech Stack

### Backend
- **Java 21** with Spring Boot 3.2
- **Python 3.9+** with FastAPI
- **PostgreSQL 15** with pgvector extension

### ML/AI
- **HuggingFace Transformers** (sentence-transformers)
- **Model**: all-MiniLM-L6-v2 (384-dimensional embeddings)

### Infrastructure
- **AWS ECS Fargate** - Container orchestration
- **AWS RDS** - Managed PostgreSQL
- **AWS ALB** - Load balancing
- **AWS ECR** - Container registry
- **Terraform** - Infrastructure as Code

### DevOps
- **Docker** & Docker Buildx
- **Maven** - Java build tool
- **pip** - Python package manager

## API Examples

### Basic Search

```bash
curl -X POST "http://<API_URL>/api/v1/search" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <YOUR_API_KEY>" \
  -d '{
    "query": "wireless bluetooth headphones",
    "limit": 10
  }'
```

### Search with Filters

```bash
curl -X POST "http://<API_URL>/api/v1/search" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <YOUR_API_KEY>" \
  -d '{
    "query": "laptop",
    "category": "Electronics",
    "minPrice": 500,
    "maxPrice": 2000,
    "minRating": 4.0,
    "limit": 20
  }'
```

### Response Format

```json
{
  "results": [
    {
      "asin": "B0C6JMP9LH",
      "title": "Wireless Earbuds, Bluetooth 5.3 Headphones...",
      "brand": "LIINCCRA",
      "price": 13.99,
      "rating": 4.0,
      "similarity": 0.5776
    }
  ],
  "totalCount": 747,
  "executionTimeMs": 340,
  "query": "wireless bluetooth headphones"
}
```

For more examples, see [TESTING.md](docs/TESTING.md).

## Testing

```bash
# Run unit tests
cd search-api
./mvnw test

# Run integration tests
./mvnw test -Dtest=*IntegrationTest

# API functional tests
curl -X POST "http://localhost:8080/api/v1/search" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: test-key" \
  -d '{"query": "test", "limit": 5}'
```

See [TESTING.md](docs/TESTING.md) for comprehensive testing guide.

## Deployment

### AWS Deployment

```bash
# 1. Deploy infrastructure
cd infrastructure
terraform init
terraform apply -var="db_password=<PASSWORD>" -var="api_key=<API_KEY>"

# 2. Build and push Docker images
./scripts/deploy.sh

# 3. Load data
cd data-pipeline
python data_ingestion.py

# 4. Test deployment
curl https://<API_URL>/api/v1/health
```

See [DEPLOYMENT.md](docs/DEPLOYMENT.md) for detailed deployment guide.

## Performance

- **Search Latency**: < 500ms (P95)
- **Throughput**: > 100 requests/second
- **Accuracy**: Semantic similarity scores 0-1 (cosine similarity)
- **Scalability**: Horizontal scaling with ECS auto-scaling

## Security

- API Key authentication for all endpoints
- AWS security groups for network isolation
- RDS encryption at rest and in transit
- IAM roles for service authentication
- Parameterized SQL queries to prevent injection

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- **HuggingFace** for transformer models
- **PostgreSQL** and **pgvector** for vector database
- **Spring Boot** and **FastAPI** frameworks

## Support

For questions or issues:
- Open an issue on GitHub
- Check [documentation](docs/)
- Review [ARCHITECTURE.md](docs/ARCHITECTURE.md) for technical details

---

**Built with for better e-commerce search experiences**
