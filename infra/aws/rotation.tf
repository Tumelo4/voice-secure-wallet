resource "aws_secretsmanager_secret_rotation" "database_password" {
  secret_id           = aws_secretsmanager_secret.database_password.id
  rotation_lambda_arn = var.database_secret_rotation_lambda_arn

  rotation_rules {
    automatically_after_days = var.secret_rotation_days
  }
}

resource "aws_secretsmanager_secret_rotation" "redis_auth_token" {
  secret_id           = aws_secretsmanager_secret.redis_auth_token.id
  rotation_lambda_arn = var.redis_secret_rotation_lambda_arn

  rotation_rules {
    automatically_after_days = var.secret_rotation_days
  }
}
