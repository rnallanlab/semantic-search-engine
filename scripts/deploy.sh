#!/bin/bash

set -e

# Configuration
AWS_REGION=${AWS_REGION:-us-east-1}
PROJECT_NAME=${PROJECT_NAME:-semantic-search}
ENVIRONMENT=${ENVIRONMENT:-prod}

# Check if required environment variables are set
if [ -z "$DB_PASSWORD" ]; then
    echo "Error: DB_PASSWORD environment variable is required"
    exit 1
fi

if [ -z "$API_KEY" ]; then
    echo "Error: API_KEY environment variable is required"
    exit 1
fi

echo "Starting deployment for $PROJECT_NAME in $ENVIRONMENT environment..."

# 1. Deploy infrastructure
echo "Deploying infrastructure..."
cd infrastructure

terraform init
terraform plan -var="db_password=$DB_PASSWORD" -var="api_key=$API_KEY"
terraform apply -var="db_password=$DB_PASSWORD" -var="api_key=$API_KEY" -auto-approve

# Get outputs
ECR_SEARCH_API=$(terraform output -raw ecr_search_api_repository_url)
ECR_ML_MODEL=$(terraform output -raw ecr_ml_model_repository_url)
RDS_ENDPOINT=$(terraform output -raw rds_endpoint)

echo "Infrastructure deployed successfully!"
echo "ECR Search API: $ECR_SEARCH_API"
echo "ECR ML Model: $ECR_ML_MODEL"
echo "RDS Endpoint: $RDS_ENDPOINT"

cd ..

# 2. Build and push Docker images
echo "Building and pushing Docker images..."

# Login to ECR
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_SEARCH_API

# Build and push ML model
echo "Building ML model image..."
cd ml-model
docker build -t $ECR_ML_MODEL:latest .
docker push $ECR_ML_MODEL:latest
cd ..

# Build and push search API
echo "Building search API image..."
cd search-api
docker build -t $ECR_SEARCH_API:latest .
docker push $ECR_SEARCH_API:latest
cd ..

# 3. Update ECS services
echo "Updating ECS services..."
aws ecs update-service \
    --cluster ${PROJECT_NAME}-${ENVIRONMENT}-cluster \
    --service ${PROJECT_NAME}-${ENVIRONMENT}-ml-model \
    --force-new-deployment \
    --region $AWS_REGION

aws ecs update-service \
    --cluster ${PROJECT_NAME}-${ENVIRONMENT}-cluster \
    --service ${PROJECT_NAME}-${ENVIRONMENT}-search-api \
    --force-new-deployment \
    --region $AWS_REGION

# 4. Wait for services to stabilize
echo "Waiting for services to stabilize..."
aws ecs wait services-stable \
    --cluster ${PROJECT_NAME}-${ENVIRONMENT}-cluster \
    --services ${PROJECT_NAME}-${ENVIRONMENT}-ml-model \
    --region $AWS_REGION

aws ecs wait services-stable \
    --cluster ${PROJECT_NAME}-${ENVIRONMENT}-cluster \
    --services ${PROJECT_NAME}-${ENVIRONMENT}-search-api \
    --region $AWS_REGION

# 5. Get API Gateway URL
API_URL=$(cd infrastructure && terraform output -raw api_gateway_url)

echo "Deployment completed successfully!"
echo "Search API URL: $API_URL/api/v1/search"
echo "Health Check: $API_URL/health"
echo ""
echo "Test the API with:"
echo "curl -X POST $API_URL/api/v1/search \\"
echo "  -H \"Content-Type: application/json\" \\"
echo "  -H \"x-api-key: $API_KEY\" \\"
echo "  -d '{\"query\": \"wireless bluetooth headphones\"}'"