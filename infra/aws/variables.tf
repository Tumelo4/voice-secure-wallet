variable "aws_region" {
  description = "AWS region for the VoiceSecure baseline."
  type        = string
  default     = "af-south-1"
}

variable "environment" {
  description = "Deployment environment name."
  type        = string
  default     = "staging"

  validation {
    condition     = contains(["staging", "production"], var.environment)
    error_message = "environment must be staging or production."
  }
}

variable "vpc_cidr" {
  description = "CIDR block for the platform VPC."
  type        = string
  default     = "10.40.0.0/16"
}

variable "private_subnet_cidrs" {
  description = "Private subnet CIDRs for application, data, and broker resources."
  type        = list(string)
  default     = ["10.40.10.0/24", "10.40.20.0/24", "10.40.30.0/24"]
}

variable "msk_broker_count" {
  description = "Number of MSK broker nodes."
  type        = number
  default     = 3
}

variable "msk_instance_type" {
  description = "MSK broker instance type."
  type        = string
  default     = "kafka.m7g.large"
}

variable "rds_instance_class" {
  description = "RDS instance class for PostgreSQL."
  type        = string
  default     = "db.m7g.large"
}

variable "rds_allocated_storage_gb" {
  description = "Initial allocated RDS storage in GB."
  type        = number
  default     = 100
}

variable "app_port" {
  description = "Application port for the private app tier."
  type        = number
  default     = 8080
}

variable "secret_rotation_days" {
  description = "Rotation interval for Secrets Manager secrets."
  type        = number
  default     = 30
}

variable "database_secret_rotation_lambda_arn" {
  description = "Lambda ARN that rotates the database password secret."
  type        = string
}

variable "redis_secret_rotation_lambda_arn" {
  description = "Lambda ARN that rotates the Redis auth token secret."
  type        = string
}

variable "rds_backup_retention_days" {
  description = "RDS point-in-time recovery retention window."
  type        = number
  default     = 35
}

variable "redis_node_type" {
  description = "ElastiCache Redis node type."
  type        = string
  default     = "cache.m7g.large"
}

variable "redis_auth_token" {
  description = "Sensitive Redis AUTH token supplied by the deployment secret store."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.redis_auth_token) >= 32
    error_message = "redis_auth_token must contain at least 32 characters."
  }
}
