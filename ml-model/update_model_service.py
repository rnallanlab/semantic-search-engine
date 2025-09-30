#!/usr/bin/env python3
"""
Update the ML model service to use a fine-tuned model

This script modifies the FastAPI service to load a fine-tuned model
instead of the base pre-trained model.
"""

import os
import shutil
import logging
from pathlib import Path

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def update_app_for_finetuned_model(fine_tuned_model_path: str):
    """Update app.py to use fine-tuned model"""

    app_file = "app.py"
    backup_file = "app_original.py"

    # Create backup
    if not os.path.exists(backup_file):
        shutil.copy(app_file, backup_file)
        logger.info(f"Created backup: {backup_file}")

    # Read current app.py
    with open(app_file, 'r') as f:
        content = f.read()

    # Update model loading logic
    updated_content = content.replace(
        'MODEL_NAME = os.getenv("MODEL_NAME", "sentence-transformers/all-MiniLM-L6-v2")',
        f'MODEL_NAME = os.getenv("MODEL_NAME", "{fine_tuned_model_path}")'
    )

    # Add fine-tuned model information to the health check
    health_check_addition = '''
    # Check if using fine-tuned model
    is_fine_tuned = "fine_tuned" in MODEL_NAME.lower() or os.path.exists(os.path.join(MODEL_NAME, "config.json"))
    '''

    # Update health check response
    updated_content = updated_content.replace(
        'return HealthResponse(',
        f'{health_check_addition}\n    return HealthResponse('
    )

    updated_content = updated_content.replace(
        'model_name=MODEL_NAME',
        'model_name=MODEL_NAME + (" (fine-tuned)" if is_fine_tuned else "")'
    )

    # Write updated app.py
    with open(app_file, 'w') as f:
        f.write(updated_content)

    logger.info(f"Updated {app_file} to use fine-tuned model: {fine_tuned_model_path}")

def create_production_dockerfile():
    """Create a Dockerfile that includes the fine-tuned model"""

    dockerfile_content = '''FROM python:3.9-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \\
    curl \\
    && rm -rf /var/lib/apt/lists/*

# Copy requirements first to leverage Docker cache
COPY requirements.txt .
COPY fine_tune_requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt
RUN pip install --no-cache-dir -r fine_tune_requirements.txt

# Copy fine-tuned model
COPY fine_tuned_ecommerce_model/ ./fine_tuned_ecommerce_model/

# Copy application code
COPY app.py .

# Create a non-root user
RUN addgroup --system appgroup && adduser --system --group appuser
RUN chown -R appuser:appgroup /app
USER appuser

# Set environment variable to use fine-tuned model
ENV MODEL_NAME=./fine_tuned_ecommerce_model

# Expose port
EXPOSE 8000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \\
  CMD curl -f http://localhost:8000/health || exit 1

# Run the application
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
'''

    with open("Dockerfile.finetuned", 'w') as f:
        f.write(dockerfile_content)

    logger.info("Created Dockerfile.finetuned for production deployment")

def create_deployment_script():
    """Create script to deploy fine-tuned model"""

    script_content = '''#!/bin/bash

set -e

echo "Deploying fine-tuned model to production..."

# Configuration
AWS_REGION=${AWS_REGION:-us-east-1}
PROJECT_NAME=${PROJECT_NAME:-semantic-search}
ENVIRONMENT=${ENVIRONMENT:-prod}

# Get ECR repository URL
cd ../infrastructure
ECR_ML_MODEL=$(terraform output -raw ecr_ml_model_repository_url)
cd ../ml-model

echo "ECR Repository: $ECR_ML_MODEL"

# Login to ECR
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_ML_MODEL

# Build image with fine-tuned model
echo "Building Docker image with fine-tuned model..."
docker build -f Dockerfile.finetuned -t $ECR_ML_MODEL:fine-tuned .

# Tag as latest
docker tag $ECR_ML_MODEL:fine-tuned $ECR_ML_MODEL:latest

# Push to ECR
echo "Pushing images to ECR..."
docker push $ECR_ML_MODEL:fine-tuned
docker push $ECR_ML_MODEL:latest

# Update ECS service
echo "Updating ECS service..."
aws ecs update-service \\
    --cluster ${PROJECT_NAME}-${ENVIRONMENT}-cluster \\
    --service ${PROJECT_NAME}-${ENVIRONMENT}-ml-model \\
    --force-new-deployment \\
    --region $AWS_REGION

# Wait for deployment to complete
echo "Waiting for service to stabilize..."
aws ecs wait services-stable \\
    --cluster ${PROJECT_NAME}-${ENVIRONMENT}-cluster \\
    --services ${PROJECT_NAME}-${ENVIRONMENT}-ml-model \\
    --region $AWS_REGION

echo "✅ Fine-tuned model deployed successfully!"

# Test the updated service
ALB_DNS=$(cd ../infrastructure && terraform output -raw alb_dns_name)
echo "Testing fine-tuned model endpoint..."

curl -X POST http://$ALB_DNS/embed \\
  -H "Content-Type: application/json" \\
  -d \'{"texts": "wireless bluetooth headphones", "normalize": true}\'

echo ""
echo "🎉 Deployment complete! Fine-tuned model is now serving requests."
'''

    with open("deploy_finetuned.sh", 'w') as f:
        f.write(script_content)

    os.chmod("deploy_finetuned.sh", 0o755)
    logger.info("Created deploy_finetuned.sh script")

def main():
    """Main function to update model service for fine-tuned model"""

    fine_tuned_path = "./fine_tuned_ecommerce_model"

    # Check if fine-tuned model exists
    if not os.path.exists(fine_tuned_path):
        logger.error(f"Fine-tuned model not found at: {fine_tuned_path}")
        logger.error("Please run the fine-tuning pipeline first:")
        logger.error("  cd ml-model && python fine_tune.py")
        return

    logger.info("Updating ML model service for fine-tuned model...")

    # Update application code
    update_app_for_finetuned_model(fine_tuned_path)

    # Create production Dockerfile
    create_production_dockerfile()

    # Create deployment script
    create_deployment_script()

    logger.info("✅ Model service updated successfully!")
    logger.info("")
    logger.info("Next steps:")
    logger.info("1. Test locally: uvicorn app:app --reload")
    logger.info("2. Deploy to production: ./deploy_finetuned.sh")
    logger.info("3. Monitor performance improvements in search results")

if __name__ == "__main__":
    main()