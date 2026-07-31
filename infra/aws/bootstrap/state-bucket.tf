terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}
provider "aws" {
  region = var.aws_region
}
variable "aws_region" {
  type    = string
  default = "af-south-1"
}
variable "state_bucket_name" {
  type    = string
  default = "voicesecure-terraform-state"
}

resource "aws_s3_bucket" "state" {
  # checkov:skip=CKV_AWS_144:Terraform state must remain in the explicitly selected South African data-residency region.
  bucket = var.state_bucket_name
  lifecycle {
    prevent_destroy = true
  }
}
resource "aws_kms_key" "bootstrap" {
  # checkov:skip=CKV2_AWS_64:The bootstrap key uses the secure AWS-managed default key policy.
  description         = "Terraform bootstrap state and lock encryption"
  enable_key_rotation = true
}
resource "aws_kms_alias" "bootstrap" {
  name          = "alias/voicesecure-terraform-bootstrap"
  target_key_id = aws_kms_key.bootstrap.key_id
}
resource "aws_s3_bucket" "state_access_logs" {
  # checkov:skip=CKV_AWS_18:This is the terminal access-log sink and cannot recursively log to itself.
  # checkov:skip=CKV_AWS_144:Terraform access logs must remain in the South African data-residency region.
  bucket = "${var.state_bucket_name}-access-logs"
}
resource "aws_s3_bucket_logging" "state" {
  bucket        = aws_s3_bucket.state.id
  target_bucket = aws_s3_bucket.state_access_logs.id
  target_prefix = "state/"
}
resource "aws_s3_bucket_versioning" "state_access_logs" {
  bucket = aws_s3_bucket.state_access_logs.id
  versioning_configuration {
    status = "Enabled"
  }
}
resource "aws_s3_bucket_server_side_encryption_configuration" "state_access_logs" {
  bucket = aws_s3_bucket.state_access_logs.id
  rule {
    apply_server_side_encryption_by_default {
      kms_master_key_id = aws_kms_key.bootstrap.arn
      sse_algorithm     = "aws:kms"
    }
  }
}
resource "aws_s3_bucket_public_access_block" "state_access_logs" {
  bucket                  = aws_s3_bucket.state_access_logs.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
resource "aws_s3_bucket_lifecycle_configuration" "state_access_logs" {
  bucket = aws_s3_bucket.state_access_logs.id
  rule {
    id     = "expire-bootstrap-access-logs"
    status = "Enabled"
    filter {}
    expiration {
      days = 365
    }
    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}
resource "aws_s3_bucket_notification" "state_access_logs" {
  bucket      = aws_s3_bucket.state_access_logs.id
  eventbridge = true
}
resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration {
    status = "Enabled"
  }
}
resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      kms_master_key_id = aws_kms_key.bootstrap.arn
      sse_algorithm     = "aws:kms"
    }
  }
}
resource "aws_s3_bucket_lifecycle_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    id     = "retain-state-versions"
    status = "Enabled"
    filter {}
    noncurrent_version_expiration {
      noncurrent_days = 365
    }
    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}
resource "aws_s3_bucket_notification" "state" {
  bucket      = aws_s3_bucket.state.id
  eventbridge = true
}
resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
resource "aws_s3_bucket_policy" "state" {
  bucket = aws_s3_bucket.state.id
  policy = jsonencode({
    Version = "2012-10-17", Statement = [{
      Sid      = "DenyInsecureTransport", Effect = "Deny", Principal = "*", Action = "s3:*",
      Resource = [aws_s3_bucket.state.arn, "${aws_s3_bucket.state.arn}/*"],
      Condition = {
        Bool = {
          "aws:SecureTransport" = "false"
        }
      }

    }]
  })
}
output "state_bucket" {
  value = aws_s3_bucket.state.bucket
}
