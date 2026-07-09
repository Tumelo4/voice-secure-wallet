resource "aws_kms_key" "platform" {
  description             = "VoiceSecure ${var.environment} platform encryption key"
  deletion_window_in_days = 30
  enable_key_rotation     = true
}

resource "aws_kms_alias" "platform" {
  name          = "alias/voicesecure-${var.environment}-platform"
  target_key_id = aws_kms_key.platform.key_id
}

resource "aws_secretsmanager_secret" "database_password" {
  name        = "voicesecure/${var.environment}/database/password"
  description = "Reference for the RDS password. Secret value is managed outside this baseline."
  kms_key_id  = aws_kms_key.platform.arn
}

resource "aws_secretsmanager_secret" "redis_auth_token" {
  name        = "voicesecure/${var.environment}/redis/auth-token"
  description = "Reference for the Redis auth token. Secret value is managed outside this baseline."
  kms_key_id  = aws_kms_key.platform.arn
}
