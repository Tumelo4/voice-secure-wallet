data "aws_partition" "current" {}

data "aws_iam_policy_document" "application_host_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "application_host" {
  name               = "voice-secure-wallet-staging-application-host"
  description        = "Runtime identity for the VoiceSecure Wallet staging application host"
  assume_role_policy = data.aws_iam_policy_document.application_host_assume.json

  tags = {
    Project     = "voice-secure-wallet"
    Component   = "application-runtime"
    Environment = "staging"
    ManagedBy   = "terraform"
    CostProfile = "free-tier"
  }
}

resource "aws_iam_role_policy_attachment" "application_host_ssm" {
  role       = aws_iam_role.application_host.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "application_host_ecr_pull" {
  statement {
    sid       = "GetEcrAuthorizationToken"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "PullApiImages"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer"
    ]
    resources = [aws_ecr_repository.api.arn]
  }
}

resource "aws_iam_role_policy" "application_host_ecr_pull" {
  name   = "voice-secure-wallet-api-image-pull"
  role   = aws_iam_role.application_host.id
  policy = data.aws_iam_policy_document.application_host_ecr_pull.json
}

resource "aws_iam_instance_profile" "application_host" {
  name = "voice-secure-wallet-staging-application-host"
  role = aws_iam_role.application_host.name

  tags = {
    Project     = "voice-secure-wallet"
    Component   = "application-runtime"
    Environment = "staging"
    ManagedBy   = "terraform"
    CostProfile = "free-tier"
  }
}
