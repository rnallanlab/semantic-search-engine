variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Name of the project"
  type        = string
  default     = "semantic-search"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "prod"
}


variable "db_name" {
  description = "Database name"
  type        = string
  default     = "ecommerce_search"
}

variable "db_username" {
  description = "Database username"
  type        = string
  default     = "postgres"
}

variable "db_password" {
  description = "Database password"
  type        = string
  sensitive   = true
}

variable "api_key" {
  description = "API key for the search service"
  type        = string
  sensitive   = true
}