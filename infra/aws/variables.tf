variable "aws_region" {
  description = "AWS region for the VoiceSecure baseline."
  type        = string
  default     = "af-south-1"
}

variable "environment" {
  description = "Deployment environment name."
  type        = string
  default     = "staging"
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
