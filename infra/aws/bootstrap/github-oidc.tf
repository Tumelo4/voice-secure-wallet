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
  github_branch_subject              = "repo:${var.github_repository_owner}/${var.github_repository_name}:ref:refs/heads/${var.github_branch_name}"
  github_staging_environment_subject = "repo:${var.github_repository_owner}/${var.github_repository_name}:environment:staging"
  staging_name                       = "voicesecure-staging"
  application_host_role_name         = "voice-secure-wallet-staging-application-host"
  application_repository_name        = "voice-secure-wallet-api"
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
      values = [
        local.github_branch_subject,
        local.github_staging_environment_subject
      ]
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

resource "aws_iam_policy" "github_actions_state" {
  name        = "voice-secure-wallet-terraform-state"
  description = "Terraform state and locking access for voice-secure-wallet"
  policy      = data.aws_iam_policy_document.github_actions_state.json

  tags = {
    Project   = "voice-secure-wallet"
    ManagedBy = "Terraform"
  }
}

resource "aws_iam_role_policy_attachment" "github_actions_state" {
  role       = aws_iam_role.github_actions.name
  policy_arn = aws_iam_policy.github_actions_state.arn
}

# Large staging permission sets use customer-managed policies so the GitHub
# Actions role does not exceed IAM's aggregate inline-policy size limit.

data "aws_iam_policy_document" "staging_application" {
  # checkov:skip=CKV_AWS_356:Selected EC2 create APIs require broad resource matching; request tags and deterministic names constrain staging creation.
  # Application runtime infrastructure: ECR, internet gateway, routes, EC2 launch and lifecycle permissions.
  statement {
    sid       = "CreateTaggedStagingApiRepository"
    actions   = ["ecr:CreateRepository"]
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
      variable = "aws:RequestTag/Project"
      values   = ["voice-secure-wallet"]
    }
  }

  statement {
    sid = "ManageStagingApiRepository"
    actions = [
      "ecr:DeleteLifecyclePolicy",
      "ecr:DeleteRepository",
      "ecr:PutImageScanningConfiguration",
      "ecr:PutImageTagMutability",
      "ecr:PutLifecyclePolicy",
      "ecr:TagResource",
      "ecr:UntagResource"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:ecr:${var.aws_region}:${data.aws_caller_identity.current.account_id}:repository/${local.application_repository_name}"
    ]
  }

  statement {
    sid       = "CreateTaggedStagingInternetGateway"
    actions   = ["ec2:CreateInternetGateway"]
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
      variable = "aws:RequestTag/Project"
      values   = ["voice-secure-wallet"]
    }
  }

  statement {
    sid = "ManageTaggedStagingInternetGateway"
    actions = [
      "ec2:AttachInternetGateway",
      "ec2:DeleteInternetGateway",
      "ec2:DetachInternetGateway"
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
    sid = "ManageTaggedStagingPublicRoute"
    actions = [
      "ec2:CreateRoute",
      "ec2:DeleteRoute",
      "ec2:ReplaceRoute"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:route-table/*"
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
    sid     = "UseApplicationLaunchDependencies"
    actions = ["ec2:RunInstances"]
    resources = [
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}::image/ami-*",
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:network-interface/*",
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:security-group/*",
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:subnet/*"
    ]
  }

  statement {
    sid     = "LaunchTaggedStagingApplicationHost"
    actions = ["ec2:RunInstances"]
    resources = [
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:instance/*",
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:volume/*"
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
      variable = "aws:RequestTag/Project"
      values   = ["voice-secure-wallet"]
    }
  }

  statement {
    sid = "ManageTaggedStagingApplicationHost"
    actions = [
      "ec2:AssociateIamInstanceProfile",
      "ec2:DisassociateIamInstanceProfile",
      "ec2:ModifyInstanceAttribute",
      "ec2:ModifyInstanceMetadataOptions",
      "ec2:RebootInstances",
      "ec2:ReplaceIamInstanceProfileAssociation",
      "ec2:StartInstances",
      "ec2:StopInstances",
      "ec2:TerminateInstances"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:instance/*"
    ]

    condition {
      test     = "StringEquals"
      variable = "ec2:ResourceTag/Environment"
      values   = ["staging"]
    }

    condition {
      test     = "StringEquals"
      variable = "ec2:ResourceTag/Project"
      values   = ["voice-secure-wallet"]
    }
  }

  statement {
    sid       = "ManageTaggedStagingApplicationVolumes"
    actions   = ["ec2:ModifyVolume"]
    resources = ["arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:volume/*"]

    condition {
      test     = "StringEquals"
      variable = "ec2:ResourceTag/Environment"
      values   = ["staging"]
    }

    condition {
      test     = "StringEquals"
      variable = "ec2:ResourceTag/Project"
      values   = ["voice-secure-wallet"]
    }
  }

  statement {
    sid       = "CreateTaggedStagingVpcEndpoint"
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
  }

  statement {
    sid = "ManageTaggedStagingVpcEndpoints"
    actions = [
      "ec2:DeleteVpcEndpoints",
      "ec2:ModifyVpcEndpoint"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:vpc-endpoint/*"
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
    sid     = "TagApplicationResourcesOnCreate"
    actions = ["ec2:CreateTags"]
    resources = [
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:instance/*",
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:internet-gateway/*",
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:network-interface/*",
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:volume/*",
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:vpc-endpoint/*",
    ]

    condition {
      test     = "StringEquals"
      variable = "ec2:CreateAction"
      values = [

        "CreateInternetGateway",

        "CreateVpcEndpoint",

        "RunInstances",

      ]
    }

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
}

resource "aws_iam_policy" "staging_application" {
  name        = "voice-secure-wallet-staging-application"
  description = "Application infrastructure provisioning permissions for staging"
  policy      = data.aws_iam_policy_document.staging_application.json

  tags = {
    Project     = "voice-secure-wallet"
    Environment = "staging"
    ManagedBy   = "Terraform"
  }
}

resource "aws_iam_role_policy_attachment" "staging_application" {
  role       = aws_iam_role.github_actions.name
  policy_arn = aws_iam_policy.staging_application.arn
}

data "aws_iam_policy_document" "staging_host_iam" {
  # Application-host IAM role, instance profile, and iam:PassRole permissions.
  statement {
    sid = "ManageApplicationHostRole"
    actions = [
      "iam:AttachRolePolicy",
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:DeleteRolePolicy",
      "iam:DetachRolePolicy",
      "iam:PutRolePolicy",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:UpdateAssumeRolePolicy"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:role/${local.application_host_role_name}"
    ]
  }

  statement {
    sid = "ManageApplicationHostInstanceProfile"
    actions = [
      "iam:AddRoleToInstanceProfile",
      "iam:CreateInstanceProfile",
      "iam:DeleteInstanceProfile",
      "iam:RemoveRoleFromInstanceProfile",
      "iam:TagInstanceProfile",
      "iam:UntagInstanceProfile"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:instance-profile/${local.application_host_role_name}",
      "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:role/${local.application_host_role_name}"
    ]
  }

  statement {
    sid       = "PassOnlyApplicationHostRole"
    actions   = ["iam:PassRole"]
    resources = ["arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:role/${local.application_host_role_name}"]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_policy" "staging_host_iam" {
  name        = "voice-secure-wallet-staging-host-iam"
  description = "Application host IAM and instance-profile permissions for staging"
  policy      = data.aws_iam_policy_document.staging_host_iam.json

  tags = {
    Project     = "voice-secure-wallet"
    Environment = "staging"
    ManagedBy   = "Terraform"
  }
}

resource "aws_iam_role_policy_attachment" "staging_host_iam" {
  role       = aws_iam_role.github_actions.name
  policy_arn = aws_iam_policy.staging_host_iam.arn
}

data "aws_iam_policy_document" "staging_network" {
  # checkov:skip=CKV_AWS_356:EC2 and VPC Flow Logs creation APIs require broad resource matching; request and resource tag conditions isolate staging.
  # Network foundation: VPC, subnets, route tables, security groups, flow logs, and the S3 gateway endpoint.
  statement {
    sid       = "CreateTaggedStagingVpc"
    actions   = ["ec2:CreateVpc"]
    resources = ["arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:vpc/*"]

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
    sid = "CreateTaggedStagingVpcResources"
    actions = [
      "ec2:CreateRouteTable",
      "ec2:CreateSecurityGroup",
      "ec2:CreateSubnet"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:route-table/*",
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:security-group/*",
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:subnet/*"
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
    sid = "UseTaggedStagingVpcForNetworkCreation"
    actions = [
      "ec2:CreateRouteTable",
      "ec2:CreateSecurityGroup",
      "ec2:CreateSubnet"
    ]
    resources = ["arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:vpc/*"]

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
    sid       = "CreateTaggedStagingFlowLog"
    actions   = ["ec2:CreateFlowLogs"]
    resources = ["arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:vpc-flow-log/*"]

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
    sid       = "UseTaggedStagingVpcForFlowLogs"
    actions   = ["ec2:CreateFlowLogs"]
    resources = ["arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:vpc/*"]

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
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:vpc-flow-log/*",
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
    resources = ["arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:vpc-endpoint/*"]

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
    sid     = "UseTaggedStagingNetworkForGatewayEndpoint"
    actions = ["ec2:CreateVpcEndpoint"]
    resources = [
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:route-table/*",
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:vpc/*"
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
}

resource "aws_iam_policy" "staging_network" {
  name        = "voice-secure-wallet-staging-network"
  description = "Network foundation provisioning permissions for staging"
  policy      = data.aws_iam_policy_document.staging_network.json

  tags = {
    Project     = "voice-secure-wallet"
    Environment = "staging"
    ManagedBy   = "Terraform"
  }
}

resource "aws_iam_role_policy_attachment" "staging_network" {
  role       = aws_iam_role.github_actions.name
  policy_arn = aws_iam_policy.staging_network.arn
}

data "aws_iam_policy_document" "staging_security" {
  # checkov:skip=CKV_AWS_356:KMS key creation requires Resource="*"; request and resource tag conditions isolate staging.
  # Security foundation: KMS, audit buckets, CloudWatch logs, and the VPC flow-log IAM role.
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
    sid       = "CreateGrantForTaggedStagingKmsKey"
    actions   = ["kms:CreateGrant"]
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

    condition {
      test     = "Bool"
      variable = "kms:GrantIsForAWSResource"
      values   = ["true"]
    }
  }

  statement {
    sid = "UseTaggedStagingKmsKeyForEcr"
    actions = [
      "kms:DescribeKey",
      "kms:RetireGrant"
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

resource "aws_iam_policy" "staging_security" {
  name        = "voice-secure-wallet-staging-security"
  description = "Security and audit infrastructure permissions for staging"
  policy      = data.aws_iam_policy_document.staging_security.json

  tags = {
    Project     = "voice-secure-wallet"
    Environment = "staging"
    ManagedBy   = "Terraform"
  }
}

resource "aws_iam_role_policy_attachment" "staging_security" {
  role       = aws_iam_role.github_actions.name
  policy_arn = aws_iam_policy.staging_security.arn
}

data "aws_iam_policy_document" "staging_delivery" {
  # checkov:skip=CKV_AWS_356:ECR authorization and SSM command-result APIs require Resource="*"; image mutation is scoped to the staging repository and deployment is scoped to the tagged staging host.
  statement {
    sid       = "GetEcrAuthorizationToken"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "PushStagingApiImage"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:DescribeRepositories",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:ecr:${var.aws_region}:${data.aws_caller_identity.current.account_id}:repository/${local.application_repository_name}"
    ]
  }

  statement {
    sid     = "UseAwsRunShellScript"
    actions = ["ssm:SendCommand"]
    resources = [
      "arn:${data.aws_partition.current.partition}:ssm:${var.aws_region}::document/AWS-RunShellScript"
    ]
  }

  statement {
    sid     = "DeployOnlyToTaggedStagingHost"
    actions = ["ssm:SendCommand"]
    resources = [
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:instance/*"
    ]

    condition {
      test     = "StringEquals"
      variable = "ssm:resourceTag/Project"
      values   = ["voice-secure-wallet"]
    }

    condition {
      test     = "StringEquals"
      variable = "ssm:resourceTag/Environment"
      values   = ["staging"]
    }
  }

  statement {
    sid = "ReadDeploymentCommandResult"
    actions = [
      "ssm:GetCommandInvocation",
      "ssm:ListCommandInvocations"
    ]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "staging_delivery" {
  name        = "voice-secure-wallet-staging-delivery"
  description = "ECR image publishing and SSM deployment permissions for staging"
  policy      = data.aws_iam_policy_document.staging_delivery.json

  tags = {
    Project     = "voice-secure-wallet"
    Environment = "staging"
    ManagedBy   = "Terraform"
  }
}

resource "aws_iam_role_policy_attachment" "staging_delivery" {
  role       = aws_iam_role.github_actions.name
  policy_arn = aws_iam_policy.staging_delivery.arn
}

output "github_actions_role_arn" {
  value = aws_iam_role.github_actions.arn
}

output "github_oidc_subject" {
  value = local.github_branch_subject
}

output "github_oidc_subjects" {
  value = [
    local.github_branch_subject,
    local.github_staging_environment_subject
  ]
}
