resource "aws_s3_bucket" "audit_evidence" {
  #checkov:skip=CKV_AWS_144:Cross-region replication is configured by the environment DR stack with its secondary-region provider.
  bucket              = "voicesecure-${var.environment}-audit-evidence"
  object_lock_enabled = true
}

resource "aws_s3_bucket_logging" "audit_evidence" {
  bucket        = aws_s3_bucket.audit_evidence.id
  target_bucket = aws_s3_bucket.access_logs.id
  target_prefix = "audit-evidence/"
}

resource "aws_s3_bucket_notification" "audit_evidence" {
  bucket      = aws_s3_bucket.audit_evidence.id
  eventbridge = true
}

resource "aws_s3_bucket_lifecycle_configuration" "audit_evidence" {
  bucket = aws_s3_bucket.audit_evidence.id
  rule {
    id     = "retain-audit-evidence"
    status = "Enabled"
    filter {}
    noncurrent_version_expiration {
      noncurrent_days = 2555
    }
    abort_incomplete_multipart_upload { days_after_initiation = 7 }
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "audit_evidence" {
  bucket = aws_s3_bucket.audit_evidence.id

  rule {
    apply_server_side_encryption_by_default {
      kms_master_key_id = aws_kms_key.platform.arn
      sse_algorithm     = "aws:kms"
    }
  }
}

resource "aws_s3_bucket_versioning" "audit_evidence" {
  bucket = aws_s3_bucket.audit_evidence.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "audit_evidence" {
  bucket                  = aws_s3_bucket.audit_evidence.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_policy" "audit_evidence" {
  bucket = aws_s3_bucket.audit_evidence.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "DenyInsecureTransport"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource = [
          aws_s3_bucket.audit_evidence.arn,
          "${aws_s3_bucket.audit_evidence.arn}/*",
        ]
        Condition = {
          Bool = {
            "aws:SecureTransport" = "false"
          }
        }
      },
    ]
  })
}

resource "aws_s3_bucket_object_lock_configuration" "audit_evidence" {
  bucket = aws_s3_bucket.audit_evidence.id

  rule {
    default_retention {
      mode = "COMPLIANCE"
      days = 365
    }
  }
}
