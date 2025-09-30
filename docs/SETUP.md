# Local Development Setup

This guide walks you through setting up the semantic search engine development environment on your local machine.

## Prerequisites

Ensure you have the following installed:

- **Java 21+** ([Download](https://adoptium.net/))
- **Python 3.9+** ([Download](https://www.python.org/downloads/))
- **Docker** ([Download](https://www.docker.com/products/docker-desktop/))
- **Docker Buildx** (for multi-platform builds)
- **PostgreSQL 15+** with **pgvector extension**
- **AWS CLI** ([Install Guide](https://aws.amazon.com/cli/))
- **Terraform** ([Download](https://www.terraform.io/downloads))

## 1. Database Setup

###  1.1 Install PostgreSQL with pgvector

**Option A: Use AWS RDS (Recommended for production)**
```bash
cd infrastructure
terraform apply -var="db_password=<YOUR_DB_PASSWORD>" -var="api_key=<YOUR_API_KEY>"
```

**Option B: Local PostgreSQL**
```bash
# Install PostgreSQL
brew install postgresql@15  # macOS
# or
sudo apt install postgresql-15  # Linux

# Start PostgreSQL
brew services start postgresql@15

# Install pgvector extension
psql postgres
CREATE EXTENSION IF NOT EXISTS vector;
\q
```

### 1.2 Create Database and Schema

```bash
# Connect to PostgreSQL
psql -h <your-db-host> -U postgres -d postgres

# Create database
CREATE DATABASE ecommerce_search;
\c ecommerce_search

# Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

# Create products table (schema automatically created by data pipeline)
```

## 2. Environment Configuration

### 2.1 Set Up Environment Variables

Create a `.env` file in the `data-pipeline/` directory:

```bash
cd data-pipeline
cp .env.example .env
```

Edit `.env` with your actual values:
```bash
DB_HOST=your-database-host.rds.amazonaws.com
DB_PORT=5432
DB_NAME=ecommerce_search
DB_USER=postgres
DB_PASSWORD=your-secure-password-here
```

### 2.2 AWS Credentials

Configure AWS credentials for deploying to ECS:

```bash
aws configure

# Enter:
# - AWS Access Key ID: <your-access-key>
# - AWS Secret Access Key: <your-secret-key>
# - Default region: us-east-1
# - Default output format: json
```

Verify credentials:
```bash
aws sts get-caller-identity
```

### 2.3 Export Environment Variables

For deployment scripts:
```bash
export DB_PASSWORD="your-db-password"
export API_KEY="your-api-key-here"
export AWS_REGION="us-east-1"
export AWS_ACCOUNT_ID="<your-aws-account-id>"
```

Or use the provided script:
```bash
source scripts/set-env.sh
```

## 3. Install Dependencies

### 3.1 Data Pipeline Dependencies

```bash
cd data-pipeline
pip install -r requirements.txt
```

### 3.2 ML Model Dependencies

```bash
cd ml-model
pip install -r requirements.txt
```

### 3.3 Search API Dependencies

Maven will automatically download dependencies:
```bash
cd search-api
./mvnw clean install
```

## 4. Load Sample Data

### 4.1 Download Dataset

The data pipeline uses e-commerce product data. Run:

```bash
cd data-pipeline
python data_ingestion.py
```

This will:
- Download the e-commerce product data
- Parse and clean the data
- Generate vector embeddings
- Insert products into PostgreSQL

**Note**: Initial data loading takes ~10-15 minutes for 1000 products.

### 4.2 Verify Data

```bash
psql -h <your-db-host> -U postgres -d ecommerce_search

SELECT COUNT(*) FROM products;
SELECT * FROM products LIMIT 5;
```

## 5. Run Services Locally

### 5.1 Start ML Model Service

```bash
cd ml-model
python app.py
```

The ML service will be available at: `http://localhost:8000`

Test the service:
```bash
curl -X POST "http://localhost:8000/embed" \
  -H "Content-Type: application/json" \
  -d '{"texts": "wireless headphones", "normalize": true}'
```

### 5.2 Start Search API

Update `search-api/src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ecommerce_search
    username: postgres
    password: ${DATABASE_PASSWORD}

embedding:
  service:
    url: http://localhost:8000

api:
  key: ${API_KEY:your-secret-api-key-here}
```

Run the API:
```bash
cd search-api
export DATABASE_PASSWORD="your-db-password"
export API_KEY="your-api-key"
./mvnw spring-boot:run
```

The Search API will be available at: `http://localhost:8080`

## 6. Verify Local Setup

Test the complete flow:

```bash
curl -X POST "http://localhost:8080/api/v1/search" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{
    "query": "wireless bluetooth headphones",
    "limit": 5
  }'
```

Expected response:
```json
{
  "results": [...],
  "totalCount": 747,
  "executionTimeMs": 340,
  "query": "wireless bluetooth headphones"
}
```

## 7. Common Issues

### Issue: "Could not connect to database"
**Solution**: Verify PostgreSQL is running and credentials are correct.

### Issue: "pgvector extension not found"
**Solution**: Install pgvector extension:
```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

### Issue: "Port already in use"
**Solution**: Kill the process using the port:
```bash
lsof -ti:8080 | xargs kill -9  # For Search API
lsof -ti:8000 | xargs kill -9  # For ML Model
```

### Issue: "Java version mismatch"
**Solution**: Ensure Java 21 is active:
```bash
export JAVA_HOME=/path/to/java-21
java -version
```

## Next Steps

- [BUILD.md](./BUILD.md) - Build Docker images and artifacts
- [DEPLOYMENT.md](./DEPLOYMENT.md) - Deploy to AWS
- [TESTING.md](./TESTING.md) - Run tests
