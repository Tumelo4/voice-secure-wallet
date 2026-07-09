resource "aws_s3_bucket" "audit_evidence" {
  bucket              = "voicesecure-${var.environment}-audit-evidence"
  object_lock_enabled = true
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
