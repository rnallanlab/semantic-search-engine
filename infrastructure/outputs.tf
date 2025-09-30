output "vpc_id" {
  description = "VPC ID"
  value       = data.aws_vpc.default.id
}

output "default_subnets" {
  description = "Default subnet IDs"
  value       = data.aws_subnets.default.ids
}

output "rds_endpoint" {
  description = "RDS endpoint"
  value       = aws_db_instance.postgres.endpoint
}

output "rds_port" {
  description = "RDS port"
  value       = aws_db_instance.postgres.port
}

output "alb_dns_name" {
  description = "DNS name of the Application Load Balancer"
  value       = aws_lb.public.dns_name
}

output "search_api_url" {
  description = "Search API URL"
  value       = "http://${aws_lb.public.dns_name}/api/v1/search"
}

output "ml_model_url" {
  description = "ML Model URL"
  value       = "http://${aws_lb.public.dns_name}/embed"
}

output "ecr_search_api_repository_url" {
  description = "ECR repository URL for search API"
  value       = aws_ecr_repository.search_api.repository_url
}

output "ecr_ml_model_repository_url" {
  description = "ECR repository URL for ML model"
  value       = aws_ecr_repository.ml_model.repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = aws_ecs_cluster.main.name
}

output "database_url" {
  description = "Database connection URL"
  value       = "postgresql://${var.db_username}:${var.db_password}@${aws_db_instance.postgres.endpoint}:${aws_db_instance.postgres.port}/${var.db_name}"
  sensitive   = true
}