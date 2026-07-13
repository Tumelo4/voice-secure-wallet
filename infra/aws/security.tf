resource "aws_kms_key" "platform" {
  description             = "VoiceSecure ${var.environment} platform encryption key"
  deletion_window_in_days = 30
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.platform_kms.json
}

data "aws_iam_policy_document" "platform_kms" {
  #checkov:skip=CKV_AWS_109:The account-root key administrator is the required recovery principal for this CMK.
  #checkov:skip=CKV_AWS_111:The account-root statement is key administration, not workload data access.
  #checkov:skip=CKV_AWS_356:KMS key policies require Resource "*" because the key is the policy attachment target.
  statement {
    sid    = "AccountAdministration"
    effect = "Allow"
    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"]
    }
    actions   = ["kms:*"]
    resources = ["*"]
  }
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
