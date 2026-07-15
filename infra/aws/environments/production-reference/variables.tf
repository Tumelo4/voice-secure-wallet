variable "aws_region" {
  type    = string
  default = "af-south-1"
}
variable "name" {
  type    = string
  default = "voicesecure-production"
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
  default = 7
}
