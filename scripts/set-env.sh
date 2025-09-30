#!/bin/bash

# Script to set environment variables for semantic search deployment
echo "=== Semantic Search Engine - Environment Setup ==="
echo

# AWS Profile
read -p "AWS Profile [semantic-search-dev]: " aws_profile
AWS_PROFILE=${aws_profile:-semantic-search-dev}
export AWS_PROFILE
echo "✓ AWS_PROFILE set to: $AWS_PROFILE"

# AWS Region
read -p "AWS Region [us-east-1]: " aws_region
AWS_REGION=${aws_region:-us-east-1}
export AWS_REGION
echo "✓ AWS_REGION set to: $AWS_REGION"

# Project Name
read -p "Project Name [semantic-search]: " project_name
PROJECT_NAME=${project_name:-semantic-search}
export PROJECT_NAME
echo "✓ PROJECT_NAME set to: $PROJECT_NAME"

# Environment
read -p "Environment [prod]: " environment
ENVIRONMENT=${environment:-prod}
export ENVIRONMENT
echo "✓ ENVIRONMENT set to: $ENVIRONMENT"

# Database Password
echo
echo "Enter a secure database password (will be hidden):"
read -s db_password
while [[ ${#db_password} -lt 8 ]]; do
    echo "Password must be at least 8 characters. Please try again:"
    read -s db_password
done
export DB_PASSWORD="$db_password"
echo "✓ DB_PASSWORD set (hidden)"

# API Key
echo
echo "Enter a secure API key (will be hidden):"
read -s api_key
while [[ ${#api_key} -lt 16 ]]; do
    echo "API key must be at least 16 characters. Please try again:"
    read -s api_key
done
export API_KEY="$api_key"
echo "✓ API_KEY set (hidden)"

echo
echo "=== Environment Variables Set ==="
echo "AWS_PROFILE: $AWS_PROFILE"
echo "AWS_REGION: $AWS_REGION"
echo "PROJECT_NAME: $PROJECT_NAME"
echo "ENVIRONMENT: $ENVIRONMENT"
echo "DB_PASSWORD: [hidden]"
echo "API_KEY: [hidden]"
echo
echo "These variables are now exported for this shell session."
echo "To use in other terminals, run: source scripts/set-env.sh"