variable "github_repository_owner" {
  type    = string
  default = "Tumelo4"
}

variable "github_repository_name" {
  type    = string
  default = "voice-secure-wallet"
}

variable "github_branch_name" {
  type    = string
  default = "main"
}

variable "github_deployment_environment_name" {
  type    = string
  default = "staging"
}

variable "github_oidc_provider_arn" {
  description = "Existing GitHub Actions OIDC provider ARN. Leave null to create it in this account."
  type        = string
  default     = null
}

data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}

resource "aws_iam_openid_connect_provider" "github" {
  count = var.github_oidc_provider_arn == null ? 1 : 0

  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

locals {
  github_oidc_provider_arn = coalesce(
    var.github_oidc_provider_arn,
    try(aws_iam_openid_connect_provider.github[0].arn, null)
  )
  github_branch_subject                 = "repo:${var.github_repository_owner}/${var.github_repository_name}:ref:refs/heads/${var.github_branch_name}"
  github_deployment_environment_subject = "repo:${var.github_repository_owner}/${var.github_repository_name}:environment:${var.github_deployment_environment_name}"
}

data "aws_iam_policy_document" "github_staging_plan_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = [local.github_branch_subject]
    }
  }
}

resource "aws_iam_role" "github_staging_plan" {
  name                 = "voicesecure-github-staging-plan"
  description          = "Short-lived GitHub Actions identity for Terraform staging plans"
  assume_role_policy   = data.aws_iam_policy_document.github_staging_plan_assume.json
  max_session_duration = 3600
}

resource "aws_iam_role_policy_attachment" "github_staging_plan_read_only" {
  role       = aws_iam_role.github_staging_plan.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/ReadOnlyAccess"
}

data "aws_iam_policy_document" "github_staging_plan_state" {
  statement {
    sid = "ListTerraformState"
    actions = [
      "s3:GetBucketLocation",
      "s3:ListBucket"
    ]
    resources = ["arn:${data.aws_partition.current.partition}:s3:::${var.state_bucket_name}"]
  }

  statement {
    sid = "ReadWriteTerraformState"
    actions = [
      "s3:GetObject",
      "s3:PutObject"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:s3:::${var.state_bucket_name}/environments/production-reference.tfstate"
    ]
  }

  statement {
    sid = "LockTerraformState"
    actions = [
      "dynamodb:DeleteItem",
      "dynamodb:DescribeTable",
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:UpdateItem"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:dynamodb:${var.aws_region}:${data.aws_caller_identity.current.account_id}:table/${var.lock_table_name}"
    ]
  }
}

resource "aws_iam_role_policy" "github_staging_plan_state" {
  name   = "terraform-state-plan-access"
  role   = aws_iam_role.github_staging_plan.id
  policy = data.aws_iam_policy_document.github_staging_plan_state.json
}

data "aws_iam_policy_document" "github_staging_deploy_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = [local.github_deployment_environment_subject]
    }
  }
}

resource "aws_iam_role" "github_staging_deploy" {
  name                 = "voicesecure-github-staging-deploy"
  description          = "Approval-protected GitHub Actions identity for staging infrastructure and ECS deployments"
  assume_role_policy   = data.aws_iam_policy_document.github_staging_deploy_assume.json
  max_session_duration = 3600
}

resource "aws_iam_role_policy_attachment" "github_staging_deploy_power_user" {
  role       = aws_iam_role.github_staging_deploy.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/PowerUserAccess"
}

data "aws_iam_policy_document" "github_staging_deploy_iam" {
  statement {
    sid = "ManageVoiceSecureServiceRoles"
    actions = [
      "iam:AttachRolePolicy",
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:DeleteRolePolicy",
      "iam:DetachRolePolicy",
      "iam:GetRole",
      "iam:GetRolePolicy",
      "iam:ListAttachedRolePolicies",
      "iam:ListRolePolicies",
      "iam:PassRole",
      "iam:PutRolePolicy",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:UpdateAssumeRolePolicy",
      "iam:UpdateRoleDescription"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:role/voicesecure-*",
      "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:role/voice-secure-wallet-*"
    ]
  }

  statement {
    sid       = "CreateRequiredAWSServiceLinkedRoles"
    actions   = ["iam:CreateServiceLinkedRole"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "iam:AWSServiceName"
      values = [
        "autoscaling.amazonaws.com",
        "elasticache.amazonaws.com",
        "kafka.amazonaws.com",
        "rds.amazonaws.com"
      ]
    }
  }
}

resource "aws_iam_role_policy" "github_staging_deploy_iam" {
  name   = "voicesecure-staging-iam"
  role   = aws_iam_role.github_staging_deploy.id
  policy = data.aws_iam_policy_document.github_staging_deploy_iam.json
}

resource "aws_iam_role_policy" "github_staging_deploy_state" {
  name   = "terraform-state-apply-access"
  role   = aws_iam_role.github_staging_deploy.id
  policy = data.aws_iam_policy_document.github_staging_plan_state.json
}

output "github_staging_plan_role_arn" {
  value = aws_iam_role.github_staging_plan.arn
}

output "github_staging_deploy_role_arn" {
  value = aws_iam_role.github_staging_deploy.arn
}

output "github_oidc_subject" {
  value = local.github_branch_subject
}

output "github_deployment_oidc_subject" {
  value = local.github_deployment_environment_subject
}
