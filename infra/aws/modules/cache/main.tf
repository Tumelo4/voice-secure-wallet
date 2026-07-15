variable "name" {
  type = string
}
variable "subnet_ids" {
  type = list(string)
}
variable "security_group_id" {
  type = string
}
variable "kms_key_arn" {
  type = string
}
variable "node_type" {
  type = string
}
variable "node_count" {
  type = number
}
variable "auth_token" {
  type      = string
  sensitive = true
}

resource "aws_elasticache_subnet_group" "this" {
  name       = "${var.name}-redis"
  subnet_ids = var.subnet_ids
}
resource "aws_elasticache_replication_group" "this" {
  replication_group_id       = "${var.name}-rate-limits"
  description                = "Distributed API rate limits"
  engine                     = "redis"
  node_type                  = var.node_type
  num_cache_clusters         = var.node_count
  automatic_failover_enabled = var.node_count > 1
  multi_az_enabled           = var.node_count > 1
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  kms_key_id                 = var.kms_key_arn
  subnet_group_name          = aws_elasticache_subnet_group.this.name
  security_group_ids         = [var.security_group_id]
  auth_token                 = var.auth_token
}
output "primary_endpoint" {
  value = aws_elasticache_replication_group.this.primary_endpoint_address
}
