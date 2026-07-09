data "aws_caller_identity" "current" {}

data "aws_iam_policy_document" "service_tasks_assume_role" {
  statement {
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}

data "aws_iam_policy_document" "ci_deploy_assume_role" {
  statement {
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["codebuild.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}

data "aws_iam_policy_document" "break_glass_assume_role" {
  statement {
    effect = "Allow"

    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"]
    }

    actions = ["sts:AssumeRole"]
  }
}

resource "aws_iam_role" "api" {
  name               = "voicesecure-${var.environment}-api"
  assume_role_policy = data.aws_iam_policy_document.service_tasks_assume_role.json
}

resource "aws_iam_role_policy" "api" {
  name = "voicesecure-${var.environment}-api"
  role = aws_iam_role.api.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "kms:Decrypt",
          "kms:Encrypt",
          "kms:GenerateDataKey",
        ]
        Resource = [aws_kms_key.platform.arn]
      },
      {
        Effect = "Allow"
        Action = ["secretsmanager:GetSecretValue"]
        Resource = [
          aws_secretsmanager_secret.database_password.arn,
          aws_secretsmanager_secret.redis_auth_token.arn,
        ]
      },
    ]
  })
}

resource "aws_iam_role" "payment" {
  name               = "voicesecure-${var.environment}-payment"
  assume_role_policy = data.aws_iam_policy_document.service_tasks_assume_role.json
}

resource "aws_iam_role_policy" "payment" {
  name = "voicesecure-${var.environment}-payment"
  role = aws_iam_role.payment.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "kms:Decrypt",
          "kms:Encrypt",
          "kms:GenerateDataKey",
        ]
        Resource = [aws_kms_key.platform.arn]
      },
      {
        Effect = "Allow"
        Action = ["secretsmanager:GetSecretValue"]
        Resource = [
          aws_secretsmanager_secret.database_password.arn,
          aws_secretsmanager_secret.redis_auth_token.arn,
        ]
      },
    ]
  })
}

resource "aws_iam_role" "ledger" {
  name               = "voicesecure-${var.environment}-ledger"
  assume_role_policy = data.aws_iam_policy_document.service_tasks_assume_role.json
}

resource "aws_iam_role_policy" "ledger" {
  name = "voicesecure-${var.environment}-ledger"
  role = aws_iam_role.ledger.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "kms:Decrypt",
          "kms:Encrypt",
          "kms:GenerateDataKey",
        ]
        Resource = [aws_kms_key.platform.arn]
      },
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:ListBucket",
          "s3:PutObject",
        ]
        Resource = [
          aws_s3_bucket.audit_evidence.arn,
          "${aws_s3_bucket.audit_evidence.arn}/*",
        ]
      },
    ]
  })
}

resource "aws_iam_role" "wallet" {
  name               = "voicesecure-${var.environment}-wallet"
  assume_role_policy = data.aws_iam_policy_document.service_tasks_assume_role.json
}

resource "aws_iam_role_policy" "wallet" {
  name = "voicesecure-${var.environment}-wallet"
  role = aws_iam_role.wallet.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "kms:Decrypt",
          "kms:GenerateDataKey",
        ]
        Resource = [aws_kms_key.platform.arn]
      },
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:ListBucket",
        ]
        Resource = [
          aws_s3_bucket.audit_evidence.arn,
          "${aws_s3_bucket.audit_evidence.arn}/*",
        ]
      },
    ]
  })
}

resource "aws_iam_role" "compliance" {
  name               = "voicesecure-${var.environment}-compliance"
  assume_role_policy = data.aws_iam_policy_document.service_tasks_assume_role.json
}

resource "aws_iam_role_policy" "compliance" {
  name = "voicesecure-${var.environment}-compliance"
  role = aws_iam_role.compliance.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "kms:Decrypt",
          "kms:GenerateDataKey",
        ]
        Resource = [aws_kms_key.platform.arn]
      },
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:ListBucket",
        ]
        Resource = [
          aws_s3_bucket.audit_evidence.arn,
          "${aws_s3_bucket.audit_evidence.arn}/*",
        ]
      },
    ]
  })
}

resource "aws_iam_role" "support" {
  name               = "voicesecure-${var.environment}-support"
  assume_role_policy = data.aws_iam_policy_document.service_tasks_assume_role.json
}

resource "aws_iam_role_policy" "support" {
  name = "voicesecure-${var.environment}-support"
  role = aws_iam_role.support.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "kms:Decrypt",
          "kms:GenerateDataKey",
        ]
        Resource = [aws_kms_key.platform.arn]
      },
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:ListBucket",
        ]
        Resource = [
          aws_s3_bucket.audit_evidence.arn,
          "${aws_s3_bucket.audit_evidence.arn}/*",
        ]
      },
    ]
  })
}

resource "aws_iam_role" "ci_deploy" {
  name               = "voicesecure-${var.environment}-ci-deploy"
  assume_role_policy = data.aws_iam_policy_document.ci_deploy_assume_role.json
}

resource "aws_iam_role_policy" "ci_deploy" {
  name = "voicesecure-${var.environment}-ci-deploy"
  role = aws_iam_role.ci_deploy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:ListBucket",
          "s3:PutObject",
        ]
        Resource = [
          aws_s3_bucket.terraform_state.arn,
          "${aws_s3_bucket.terraform_state.arn}/*",
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "dynamodb:DeleteItem",
          "dynamodb:DescribeTable",
          "dynamodb:GetItem",
          "dynamodb:PutItem",
        ]
        Resource = [aws_dynamodb_table.terraform_locks.arn]
      },
    ]
  })
}

resource "aws_iam_role" "break_glass_admin" {
  name               = "voicesecure-${var.environment}-break-glass-admin"
  assume_role_policy = data.aws_iam_policy_document.break_glass_assume_role.json
}
