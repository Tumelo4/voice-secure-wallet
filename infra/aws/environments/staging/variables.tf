variable "aws_region" {
  type    = string
  default = "af-south-1"
}

variable "name" {
  type    = string
  default = "voicesecure-staging"
}

variable "redis_auth_token" {
  type      = string
  sensitive = true

  validation {
    condition     = length(var.redis_auth_token) >= 32
    error_message = "redis_auth_token must be at least 32 characters."
  }
}

variable "enable_msk" {
  type    = bool
  default = false
}

variable "enable_rds" {
  type    = bool
  default = false
}

variable "enable_redis" {
  type    = bool
  default = false
}

variable "interface_endpoint_services" {
  type    = set(string)
  default = []
}

variable "rds_multi_az" {
  type    = bool
  default = false
}

variable "rds_deletion_protection" {
  type    = bool
  default = false
}

variable "rds_performance_insights_enabled" {
  type    = bool
  default = false
}

variable "redis_node_count" {
  type    = number
  default = 1
}

variable "redis_multi_az" {
  type    = bool
  default = false
}

variable "audit_object_lock_enabled" {
  type    = bool
  default = false
}

variable "log_retention_days" {
  type    = number
  default = 30
}

variable "application_instance_type" {
  description = "EC2 instance class for the single staging application host."
  type        = string
  default     = "t3.micro"

  validation {
    condition     = contains(["t3.micro", "t2.micro"], var.application_instance_type)
    error_message = "application_instance_type must remain a micro instance for the staging cost profile."
  }
}

variable "application_ami_id" {
  description = "Optional pinned Amazon Linux 2023 AMI. Leave null to discover the latest x86_64 image when creating a host."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.application_ami_id == null || can(regex("^ami-[0-9a-f]+$", var.application_ami_id))
    error_message = "application_ami_id must be null or a valid AMI identifier."
  }
}

variable "application_public_subnet_cidr" {
  description = "CIDR for the public subnet hosting the low-cost staging runtime."
  type        = string
  default     = "10.45.30.0/24"

  validation {
    condition     = can(cidrnetmask(var.application_public_subnet_cidr))
    error_message = "application_public_subnet_cidr must be a valid IPv4 CIDR."
  }
}

variable "application_ingress_cidrs" {
  description = "IPv4 CIDRs allowed to reach the staging API on port 80. Narrow this list when public access is not required."
  type        = set(string)
  default     = ["0.0.0.0/0"]

  validation {
    condition     = length(var.application_ingress_cidrs) > 0 && alltrue([for cidr in var.application_ingress_cidrs : can(cidrnetmask(cidr))])
    error_message = "application_ingress_cidrs must contain at least one valid IPv4 CIDR."
  }
}

variable "application_root_volume_size_gb" {
  description = "Encrypted gp3 root volume size for the staging application host."
  type        = number
  default     = 8

  validation {
    condition     = var.application_root_volume_size_gb >= 8 && var.application_root_volume_size_gb <= 30
    error_message = "application_root_volume_size_gb must be between 8 and 30 GiB."
  }
}
