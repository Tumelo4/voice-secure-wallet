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

  tags = {
    Project   = "voice-secure-wallet"
    ManagedBy = "Terraform"
  }
}

locals {
  github_oidc_provider_arn = coalesce(
    var.github_oidc_provider_arn,
    try(aws_iam_openid_connect_provider.github[0].arn, null)
  )
  github_branch_subject = "repo:${var.github_repository_owner}/${var.github_repository_name}:ref:refs/heads/${var.github_branch_name}"
  staging_name          = "voicesecure-staging"
  staging_bucket_arns = [
    "arn:${data.aws_partition.current.partition}:s3:::${local.staging_name}-access-logs",
    "arn:${data.aws_partition.current.partition}:s3:::${local.staging_name}-audit-evidence"
  ]
}

data "aws_iam_policy_document" "github_actions_assume" {
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

resource "aws_iam_role" "github_actions" {
  name                 = "voice-secure-wallet"
  description          = "Short-lived GitHub Actions identity for voice-secure-wallet staging plans and applies"
  assume_role_policy   = data.aws_iam_policy_document.github_actions_assume.json
  max_session_duration = 3600

  tags = {
    Project   = "voice-secure-wallet"
    ManagedBy = "Terraform"
  }
}

resource "aws_iam_role_policy_attachment" "github_actions_read_only" {
  role       = aws_iam_role.github_actions.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/ReadOnlyAccess"
}

data "aws_iam_policy_document" "github_actions_state" {
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
      "arn:${data.aws_partition.current.partition}:s3:::${var.state_bucket_name}/environments/production-reference.tfstate",
      "arn:${data.aws_partition.current.partition}:s3:::${var.state_bucket_name}/environments/staging.tfstate"
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

  statement {
    sid = "LockStagingTerraformState"
    actions = [
      "s3:DeleteObject",
      "s3:GetObject",
      "s3:PutObject"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:s3:::${var.state_bucket_name}/environments/staging.tfstate.tflock"
    ]
  }

  statement {
    sid = "UseTerraformStateKmsKey"
    actions = [
      "kms:Decrypt",
      "kms:DescribeKey",
      "kms:Encrypt",
      "kms:GenerateDataKey"
    ]
    resources = [aws_kms_key.bootstrap.arn]
  }
}

data "aws_iam_policy_document" "github_actions_deploy" {
  # checkov:skip=CKV_AWS_356:AWS requires Resource="*" for ECR authorization and ECS register/list/describe APIs; all restrictable deploy actions remain scoped to project resources.
  statement {
    sid       = "GetECRAuthorizationToken"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "PushVoiceSecureWalletImages"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
      "ecr:DescribeImages",
      "ecr:DescribeRepositories"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:ecr:${var.aws_region}:${data.aws_caller_identity.current.account_id}:repository/voice-secure-wallet-*"
    ]
  }

  statement {
    sid = "RegisterECSTaskDefinitions"
    actions = [
      "ecs:RegisterTaskDefinition",
      "ecs:DescribeTaskDefinition",
      "ecs:ListTaskDefinitions",
      "ecs:TagResource"
    ]
    resources = ["*"]
  }

  statement {
    sid = "DeployVoiceSecureWalletServices"
    actions = [
      "ecs:UpdateService",
      "ecs:DescribeServices"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:service/voice-secure-wallet-*/voice-secure-wallet-*"
    ]
  }

  statement {
    sid = "ReadECSDeploymentState"
    actions = [
      "ecs:DescribeClusters",
      "ecs:ListTasks",
      "ecs:DescribeTasks"
    ]
    resources = ["*"]
  }

  statement {
    sid     = "PassOnlyVoiceSecureWalletTaskRoles"
    actions = ["iam:PassRole"]
    resources = [
      "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:role/voice-secure-wallet-*-task-role",
      "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:role/voice-secure-wallet-*-execution-role"
    ]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "github_actions_deploy" {
  name   = "voice-secure-wallet-deploy"
  role   = aws_iam_role.github_actions.id
  policy = data.aws_iam_policy_document.github_actions_deploy.json
}

resource "aws_iam_role_policy" "github_actions_state" {
  name   = "terraform-state-apply-access"
  role   = aws_iam_role.github_actions.id
  policy = data.aws_iam_policy_document.github_actions_state.json
}

data "aws_iam_policy_document" "github_actions_staging_foundation" {
  # checkov:skip=CKV_AWS_356:EC2, VPC Flow Logs, and KMS creation APIs require Resource="*"; request and resource tag conditions isolate staging.
  statement {
    sid = "CreateTaggedStagingNetwork"
    actions = [
      "ec2:CreateFlowLogs",
      "ec2:CreateRouteTable",
      "ec2:CreateSecurityGroup",
      "ec2:CreateSubnet",
      "ec2:CreateVpc"
    ]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestTag/Environment"
      values   = ["staging"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:RequestTag/ManagedBy"
      values   = ["terraform"]
    }
  }

  statement {
    sid = "ManageTaggedStagingNetwork"
    actions = [
      "ec2:AssociateRouteTable", "ec2:DeleteFlowLogs", "ec2:DeleteRouteTable",
      "ec2:DeleteSecurityGroup", "ec2:DeleteSubnet", "ec2:DeleteTags", "ec2:DeleteVpc",
      "ec2:DisassociateRouteTable", "ec2:ModifySubnetAttribute", "ec2:ModifyVpcAttribute"
    ]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "ec2:ResourceTag/Environment"
      values   = ["staging"]
    }
    condition {
      test     = "StringEquals"
      variable = "ec2:ResourceTag/ManagedBy"
      values   = ["terraform"]
    }
  }

  statement {
    sid     = "TagStagingNetworkOnCreate"
    actions = ["ec2:CreateTags"]
    resources = [
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:flow-log/*",
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:route-table/*",
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:security-group/*",
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:security-group-rule/*",
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:subnet/*",
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:vpc-endpoint/*",
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:vpc/*"
    ]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestTag/Environment"
      values   = ["staging"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:RequestTag/ManagedBy"
      values   = ["terraform"]
    }
    condition {
      test     = "StringEquals"
      variable = "ec2:CreateAction"
      values = [
        "AuthorizeSecurityGroupEgress", "AuthorizeSecurityGroupIngress", "CreateFlowLogs",
        "CreateRouteTable", "CreateSecurityGroup", "CreateSubnet", "CreateVpc", "CreateVpcEndpoint"
      ]
    }
  }

  statement {
    sid     = "CreateTaggedStagingSecurityGroupRules"
    actions = ["ec2:AuthorizeSecurityGroupEgress", "ec2:AuthorizeSecurityGroupIngress"]
    resources = [
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:security-group-rule/*"
    ]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestTag/Environment"
      values   = ["staging"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:RequestTag/ManagedBy"
      values   = ["terraform"]
    }
  }

  statement {
    sid = "ManageRulesForTaggedStagingSecurityGroups"
    actions = [
      "ec2:AuthorizeSecurityGroupEgress", "ec2:AuthorizeSecurityGroupIngress",
      "ec2:RevokeSecurityGroupEgress", "ec2:RevokeSecurityGroupIngress"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:security-group/*"
    ]
    condition {
      test     = "StringEquals"
      variable = "ec2:ResourceTag/Environment"
      values   = ["staging"]
    }
    condition {
      test     = "StringEquals"
      variable = "ec2:ResourceTag/ManagedBy"
      values   = ["terraform"]
    }
  }

  statement {
    sid     = "DeleteTaggedStagingSecurityGroupRules"
    actions = ["ec2:RevokeSecurityGroupEgress", "ec2:RevokeSecurityGroupIngress"]
    resources = [
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:security-group-rule/*"
    ]
    condition {
      test     = "StringEquals"
      variable = "ec2:ResourceTag/Environment"
      values   = ["staging"]
    }
    condition {
      test     = "StringEquals"
      variable = "ec2:ResourceTag/ManagedBy"
      values   = ["terraform"]
    }
  }

  # A VPC's default security group predates Terraform and therefore cannot be
  # protected by create-time tags. Only the deterministic staging Name tag may
  # bootstrap that one resource into the normal resource-tag boundary above.
  statement {
    sid       = "TagStagingDefaultSecurityGroup"
    actions   = ["ec2:CreateTags"]
    resources = ["arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:security-group/*"]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestTag/Environment"
      values   = ["staging"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:RequestTag/ManagedBy"
      values   = ["terraform"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:RequestTag/Name"
      values   = ["${local.staging_name}-default"]
    }
  }

  statement {
    sid       = "CreateTaggedStagingGatewayEndpoint"
    actions   = ["ec2:CreateVpcEndpoint"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestTag/Environment"
      values   = ["staging"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:RequestTag/ManagedBy"
      values   = ["terraform"]
    }
    condition {
      test     = "StringEquals"
      variable = "ec2:VpceServiceName"
      values   = ["com.amazonaws.${var.aws_region}.s3"]
    }
  }

  statement {
    sid       = "ManageTaggedStagingGatewayEndpoint"
    actions   = ["ec2:DeleteVpcEndpoints", "ec2:ModifyVpcEndpoint"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "ec2:ResourceTag/Environment"
      values   = ["staging"]
    }
    condition {
      test     = "StringEquals"
      variable = "ec2:ResourceTag/ManagedBy"
      values   = ["terraform"]
    }
  }

  statement {
    sid       = "CreateTaggedStagingKmsKey"
    actions   = ["kms:CreateKey"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestTag/Environment"
      values   = ["staging"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:RequestTag/ManagedBy"
      values   = ["terraform"]
    }
  }

  statement {
    sid = "ManageTaggedStagingKmsKey"
    actions = [
      "kms:CancelKeyDeletion", "kms:DisableKeyRotation", "kms:EnableKeyRotation", "kms:PutKeyPolicy",
      "kms:ScheduleKeyDeletion", "kms:TagResource", "kms:UntagResource", "kms:UpdateKeyDescription"
    ]
    resources = ["arn:${data.aws_partition.current.partition}:kms:${var.aws_region}:${data.aws_caller_identity.current.account_id}:key/*"]
    condition {
      test     = "StringEquals"
      variable = "aws:ResourceTag/Environment"
      values   = ["staging"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:ResourceTag/ManagedBy"
      values   = ["terraform"]
    }
  }

  statement {
    sid     = "ManageStagingKmsAlias"
    actions = ["kms:CreateAlias", "kms:DeleteAlias", "kms:UpdateAlias"]
    resources = [
      "arn:${data.aws_partition.current.partition}:kms:${var.aws_region}:${data.aws_caller_identity.current.account_id}:alias/${local.staging_name}-platform",
      "arn:${data.aws_partition.current.partition}:kms:${var.aws_region}:${data.aws_caller_identity.current.account_id}:key/*"
    ]
  }

  statement {
    sid = "ManageStagingAuditBuckets"
    actions = [
      "s3:CreateBucket", "s3:DeleteBucket", "s3:DeleteBucketPolicy", "s3:GetBucketPolicy",
      "s3:PutLifecycleConfiguration", "s3:PutBucketLogging", "s3:PutBucketNotification",
      "s3:PutBucketPolicy", "s3:PutBucketPublicAccessBlock", "s3:PutBucketTagging",
      "s3:PutBucketVersioning", "s3:PutEncryptionConfiguration"
    ]
    resources = local.staging_bucket_arns
  }

  statement {
    sid = "ManageStagingLogGroups"
    actions = [
      "logs:AssociateKmsKey", "logs:CreateLogGroup", "logs:DeleteLogGroup", "logs:DeleteRetentionPolicy",
      "logs:DisassociateKmsKey", "logs:PutRetentionPolicy", "logs:TagResource", "logs:UntagResource"
    ]
    resources = ["arn:${data.aws_partition.current.partition}:logs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:log-group:/${local.staging_name}/*"]
  }

  statement {
    sid = "ManageStagingFlowLogRole"
    actions = [
      "iam:CreateRole", "iam:DeleteRole", "iam:DeleteRolePolicy", "iam:PutRolePolicy",
      "iam:TagRole", "iam:UntagRole", "iam:UpdateAssumeRolePolicy"
    ]
    resources = ["arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:role/${local.staging_name}-vpc-flow"]
  }

  statement {
    sid       = "PassOnlyStagingFlowLogRole"
    actions   = ["iam:PassRole"]
    resources = ["arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:role/${local.staging_name}-vpc-flow"]
    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["vpc-flow-logs.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "github_actions_staging_foundation" {
  name   = "staging-foundation-apply-access"
  role   = aws_iam_role.github_actions.id
  policy = data.aws_iam_policy_document.github_actions_staging_foundation.json
}

output "github_actions_role_arn" {
  value = aws_iam_role.github_actions.arn
}

output "github_oidc_subject" {
  value = local.github_branch_subject
}
