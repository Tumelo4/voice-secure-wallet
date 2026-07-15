variable "aws_region" {
  type    = string
  default = "af-south-1"
}
variable "name" {
  type    = string
  default = "voicesecure-demo"
}
variable "redis_auth_token" {
  type      = string
  sensitive = true
  validation {
    condition     = length(var.redis_auth_token) >= 32
    error_message = "redis_auth_token must be at least 32 characters."
  }
}
