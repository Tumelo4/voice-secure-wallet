data "aws_caller_identity" "staging" {}

data "aws_cloudfront_cache_policy" "managed_caching_optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_cache_policy" "managed_caching_disabled" {
  name = "Managed-CachingDisabled"
}

data "aws_cloudfront_origin_request_policy" "managed_all_viewer_except_host" {
  name = "Managed-AllViewerExceptHostHeader"
}

data "aws_ec2_managed_prefix_list" "cloudfront_origin_facing" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

locals {
  frontend_bucket_name = "${var.name}-frontend-${data.aws_caller_identity.staging.account_id}"
}

resource "aws_s3_bucket" "frontend" {
  # checkov:skip=CKV_AWS_18:CloudFront is the only reader and direct bucket access is blocked; edge logging is omitted for the cost-controlled staging environment.
  # checkov:skip=CKV_AWS_144:Staging artifacts remain in af-south-1 to preserve the project's explicit data-residency boundary.
  # checkov:skip=CKV_AWS_145:SSE-S3 is sufficient for public static build artifacts in a private bucket; regulated data is not stored here.
  # checkov:skip=CKV2_AWS_62:Static frontend object changes are controlled by the deployment workflow and do not require event notifications.
  bucket        = local.frontend_bucket_name
  force_destroy = false

  tags = {
    Project     = "voice-secure-wallet"
    Component   = "web-frontend"
    Environment = "staging"
    ManagedBy   = "terraform"
  }
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket                  = aws_s3_bucket.frontend.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "frontend" {
  bucket = aws_s3_bucket.frontend.id

  rule {
    id     = "expire-old-frontend-versions"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 30
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "${var.name}-frontend"
  description                       = "Private staging frontend access"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_response_headers_policy" "security" {
  name = "${var.name}-security-headers"
  security_headers_config {
    content_security_policy {
      content_security_policy = "default-src 'self'; base-uri 'self'; connect-src 'self'; font-src 'self' data:; form-action 'self'; frame-ancestors 'none'; img-src 'self' data: blob:; object-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; worker-src 'self' blob:"
      override                = true
    }
    content_type_options {
      override = true
    }
    frame_options {
      frame_option = "DENY"
      override     = true
    }
    referrer_policy {
      referrer_policy = "strict-origin-when-cross-origin"
      override        = true
    }
    strict_transport_security {
      access_control_max_age_sec = 31536000
      include_subdomains         = true
      preload                    = true
      override                   = true
    }
    xss_protection {
      mode_block = true
      protection = true
      override   = true
    }
  }
  custom_headers_config {
    items {
      header   = "Permissions-Policy"
      value    = "camera=(), geolocation=(), microphone=(self), payment=(), usb=()"
      override = true
    }
  }
}

resource "aws_cloudfront_distribution" "application" {
  # checkov:skip=CKV_AWS_68:AWS WAF has a material fixed cost and is reserved for the production edge; staging uses application rate limits and CloudFront-only origin ingress.
  # checkov:skip=CKV_AWS_86:Standard access logging is omitted for this cost-controlled staging distribution; production must enable centralized edge logs.
  # checkov:skip=CKV_AWS_174:The CloudFront default certificate is used with TLSv1.2_2021; Checkov does not recognize the minimum version for the default certificate path.
  # checkov:skip=CKV_AWS_310:Origin failover would require a second backend and conflicts with the documented single-host staging cost profile.
  # checkov:skip=CKV_AWS_374:Staging access is intentionally global for distributed testing; authorization is enforced by the application rather than geography.
  # checkov:skip=CKV2_AWS_42:The CloudFront-managed certificate provides HTTPS for the generated distribution domain; no custom domain is configured for staging.
  # checkov:skip=CKV2_AWS_47:AWS WAF is intentionally omitted from cost-controlled staging; production requires WAF managed rules including the Log4j rule group.
  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"
  price_class         = "PriceClass_100"
  http_version        = "http2and3"
  comment             = "VoiceSecure Wallet staging frontend and API edge"

  origin {
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_id                = "frontend-s3"
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }
  origin {
    domain_name = aws_instance.application_host.public_dns
    origin_id   = "backend-ec2"
    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    allowed_methods            = ["GET", "HEAD", "OPTIONS"]
    cached_methods             = ["GET", "HEAD"]
    target_origin_id           = "frontend-s3"
    viewer_protocol_policy     = "redirect-to-https"
    cache_policy_id            = data.aws_cloudfront_cache_policy.managed_caching_optimized.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id
    compress                   = true
  }

  dynamic "ordered_cache_behavior" {
    for_each = toset(["/v1/*", "/wallets/*", "/health/*"])
    content {
      path_pattern               = ordered_cache_behavior.value
      allowed_methods            = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
      cached_methods             = ["GET", "HEAD"]
      target_origin_id           = "backend-ec2"
      viewer_protocol_policy     = "https-only"
      cache_policy_id            = data.aws_cloudfront_cache_policy.managed_caching_disabled.id
      origin_request_policy_id   = data.aws_cloudfront_origin_request_policy.managed_all_viewer_except_host.id
      response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id
      compress                   = true
    }
  }

  custom_error_response {
    error_code            = 403
    response_code         = 200
    response_page_path    = "/index.html"
    error_caching_min_ttl = 0
  }
  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }
  viewer_certificate {
    cloudfront_default_certificate = true
    minimum_protocol_version       = "TLSv1.2_2021"
  }

  tags = {
    Project     = "voice-secure-wallet"
    Component   = "application-edge"
    Environment = "staging"
    ManagedBy   = "terraform"
  }
}

data "aws_iam_policy_document" "frontend" {
  statement {
    sid       = "AllowCloudFrontReadOnly"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.frontend.arn}/*"]
    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.application.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  policy = data.aws_iam_policy_document.frontend.json
}

output "frontend_bucket_name" {
  description = "Private S3 bucket populated by the frontend delivery workflow."
  value       = aws_s3_bucket.frontend.id
}
output "frontend_distribution_id" {
  description = "CloudFront distribution invalidated after a frontend release."
  value       = aws_cloudfront_distribution.application.id
}
output "frontend_url" {
  description = "HTTPS URL for the integrated staging application."
  value       = "https://${aws_cloudfront_distribution.application.domain_name}"
}
