output "vpc_id" {
  description = "VoiceSecure VPC ID."
  value       = aws_vpc.voice_secure.id
}

output "private_subnet_ids" {
  description = "Private subnet IDs."
  value       = [for subnet in aws_subnet.private : subnet.id]
}

output "kms_key_arn" {
  description = "Platform KMS key ARN."
  value       = aws_kms_key.platform.arn
}

output "msk_bootstrap_brokers_tls" {
  description = "MSK TLS bootstrap brokers."
  value       = aws_msk_cluster.events.bootstrap_brokers_tls
}

output "ledger_database_endpoint" {
  description = "RDS PostgreSQL endpoint."
  value       = aws_db_instance.ledger.endpoint
}

output "redis_primary_endpoint" {
  description = "Redis primary endpoint."
  value       = aws_elasticache_replication_group.api_rate_limits.primary_endpoint_address
}

output "audit_evidence_bucket" {
  description = "S3 bucket for audit evidence."
  value       = aws_s3_bucket.audit_evidence.bucket
}
