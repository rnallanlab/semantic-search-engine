# Deployment Guide

This guide walks you through deploying the semantic search engine to AWS.

## Prerequisites

1. **AWS CLI configured** with appropriate permissions
2. **Terraform** >= 1.0 installed
3. **Docker** installed and running
4. **Java 21+** for local development
5. **Python 3.9+** for data pipeline

## Required AWS Permissions

Your AWS user/role needs the following permissions:
- EC2, VPC, Security Groups
- RDS (PostgreSQL)
- ECS, Fargate
- ECR
- Application Load Balancer
- CloudWatch Logs
- IAM (for creating service roles)

## Environment Variables

Set these environment variables before deployment:

```bash
export AWS_REGION=us-east-1
export PROJECT_NAME=semantic-search
export ENVIRONMENT=prod
export DB_PASSWORD=your-secure-database-password
export API_KEY=your-secret-api-key-here
```

## Step 1: Deploy Infrastructure

```bash
# Navigate to infrastructure directory
cd infrastructure

# Initialize Terraform
terraform init

# Review the plan
terraform plan -var="db_password=$DB_PASSWORD" -var="api_key=$API_KEY"

# Deploy infrastructure
terraform apply -var="db_password=$DB_PASSWORD" -var="api_key=$API_KEY"
```

This creates:
- PostgreSQL RDS instance with pgvector (single AZ)
- ECS Fargate cluster (single AZ)
- API Gateway with VPC Link
- Internal Load Balancer for service communication
- ECR repositories
- Security groups and IAM roles

## Step 2: Set Up Database

```bash
# Run database setup script
./scripts/setup-database.sh
```

This script:
- Enables pgvector extension
- Creates products table with vector columns
- Sets up indexes for optimal performance
- Configures triggers

## Step 3: Build and Deploy Applications

### Option A: Full Automated Deployment

```bash
./scripts/deploy.sh
```

### Option B: Manual Step-by-Step

1. **Get ECR repository URLs:**
```bash
cd infrastructure
ECR_SEARCH_API=$(terraform output -raw ecr_search_api_repository_url)
ECR_ML_MODEL=$(terraform output -raw ecr_ml_model_repository_url)
```

2. **Login to ECR:**
```bash
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_SEARCH_API
```

3. **Build and push ML model:**
```bash
cd ml-model
docker build -t $ECR_ML_MODEL:latest .
docker push $ECR_ML_MODEL:latest
```

4. **Build and push Search API:**
```bash
cd ../search-api
docker build -t $ECR_SEARCH_API:latest .
docker push $ECR_SEARCH_API:latest
```

5. **Update ECS services:**
```bash
aws ecs update-service \
    --cluster ${PROJECT_NAME}-${ENVIRONMENT}-cluster \
    --service ${PROJECT_NAME}-${ENVIRONMENT}-ml-model \
    --force-new-deployment

aws ecs update-service \
    --cluster ${PROJECT_NAME}-${ENVIRONMENT}-cluster \
    --service ${PROJECT_NAME}-${ENVIRONMENT}-search-api \
    --force-new-deployment
```

## Step 4: Load Data

```bash
cd data-pipeline

# Set environment variables for database connection
export DATABASE_URL="postgresql://postgres:$DB_PASSWORD@$(cd ../infrastructure && terraform output -raw rds_endpoint | cut -d: -f1):5432/ecommerce_search"

# Install Python dependencies
pip install -r requirements.txt

# Run data ingestion (downloads products CSV from GitHub)
python data_ingestion.py
```

**Data Source**: The pipeline downloads e-commerce product data from:
`https://raw.githubusercontent.com/luminati-io/eCommerce-dataset-samples/main/ecommerce-products.csv`

This CSV contains real e-commerce product data with columns for:
- Product titles, descriptions, brands
- Pricing and availability
- Ratings and review counts
- Categories and ASIN identifiers
- Product images and metadata

## Step 5: Test the API

```bash
# Get API Gateway URL
API_URL=$(cd infrastructure && terraform output -raw api_gateway_url)

# Test health endpoint
curl $API_URL/health

# Test search endpoint
curl -X POST $API_URL/api/v1/search \
  -H "Content-Type: application/json" \
  -H "x-api-key: $API_KEY" \
  -d '{"query": "wireless bluetooth headphones", "limit": 5}'
```

## API Endpoints

### Search API
- **Base URL**: `https://{API_GATEWAY_ID}.execute-api.{REGION}.amazonaws.com/{STAGE}`
- **Authentication**: `x-api-key` header required

#### POST /api/v1/search
Main semantic search endpoint.

**Request:**
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

**Response:**
```json
{
  "results": [
    {
      "asin": "B08C7KG5LP",
      "title": "Sony WH-1000XM4 Wireless Noise Canceling Headphones",
      "description": "...",
      "brand": "Sony",
      "category": ["Electronics", "Headphones"],
      "price": 179.99,
      "rating": 4.5,
      "similarity": 0.87
    }
  ],
  "totalCount": 150,
  "limit": 10,
  "offset": 0,
  "query": "wireless bluetooth headphones",
  "executionTimeMs": 45
}
```

#### POST /api/v1/search/title
Search by title similarity only.

#### POST /api/v1/search/description
Search by description similarity only.

#### GET /health
Health check endpoint.

### ML Model API
- **Base URL**: `http://{ALB_DNS}` (internal routing)

#### POST /embed
Generate embeddings for text.

**Request:**
```json
{
  "texts": "wireless bluetooth headphones",
  "normalize": true
}
```

## Monitoring and Logs

### CloudWatch Logs
- Search API: `/ecs/semantic-search-prod/search-api`
- ML Model: `/ecs/semantic-search-prod/ml-model`

### ECS Service Monitoring
```bash
# Check service status
aws ecs describe-services \
    --cluster semantic-search-prod-cluster \
    --services semantic-search-prod-search-api

# View logs
aws logs tail /ecs/semantic-search-prod/search-api --follow
```

## Scaling

### Auto Scaling
The ECS services can be configured with auto-scaling policies:

```bash
# Example: Scale search API based on CPU
aws application-autoscaling register-scalable-target \
    --service-namespace ecs \
    --resource-id service/semantic-search-prod-cluster/semantic-search-prod-search-api \
    --scalable-dimension ecs:service:DesiredCount \
    --min-capacity 2 \
    --max-capacity 10
```

### Manual Scaling
```bash
# Scale search API to 4 instances
aws ecs update-service \
    --cluster semantic-search-prod-cluster \
    --service semantic-search-prod-search-api \
    --desired-count 4
```

## Cost Optimization

### Development Environment
For development, use smaller instance types:
- RDS: `db.t3.micro` (included in free tier)
- ECS: Reduce CPU/memory allocations
- Reduce desired count to 1 for each service

### Production Optimizations
- Use Reserved Instances for predictable workloads
- Enable ECS Service auto-scaling
- Use CloudFront for API caching
- Implement database connection pooling

## Cleanup

To destroy all resources:

```bash
cd infrastructure
terraform destroy -var="db_password=$DB_PASSWORD" -var="api_key=$API_KEY"
```

**Warning**: This will permanently delete all data and resources.

## Troubleshooting

### Common Issues

1. **ECS Tasks Failing to Start**
   - Check CloudWatch logs for application errors
   - Verify environment variables
   - Ensure ECR images are pushed correctly

2. **Database Connection Issues**
   - Verify security group rules
   - Check database credentials
   - Ensure pgvector extension is installed

3. **Search Returns No Results**
   - Verify data was loaded correctly
   - Check embedding service is running
   - Adjust similarity threshold

4. **API Authentication Errors**
   - Verify `x-api-key` header is included
   - Check API key matches environment variable

### Useful Commands

```bash
# Check ECS task logs
aws logs get-log-events \
    --log-group-name /ecs/semantic-search-prod/search-api \
    --log-stream-name $(aws logs describe-log-streams \
        --log-group-name /ecs/semantic-search-prod/search-api \
        --order-by LastEventTime --descending \
        --query 'logStreams[0].logStreamName' --output text)

# Connect to database
psql -h $(cd infrastructure && terraform output -raw rds_endpoint | cut -d: -f1) \
     -p 5432 -U postgres -d ecommerce_search

# Check ECS service health
aws ecs describe-services \
    --cluster semantic-search-prod-cluster \
    --services semantic-search-prod-search-api \
    --query 'services[0].deployments[0].status'
```