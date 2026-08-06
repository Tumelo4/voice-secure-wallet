resource "aws_ecr_repository" "api" {
  name                 = "voice-secure-wallet-api"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "KMS"
    kms_key         = module.encryption.key_arn
  }

  tags = {
    Project     = "voice-secure-wallet"
    Component   = "api-adapter"
    Environment = "staging"
    ManagedBy   = "terraform"
    CostProfile = "free-tier"
  }
}

resource "aws_ecr_lifecycle_policy" "api" {
  repository = aws_ecr_repository.api.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Retain the five most recent immutable API images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 5
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
