variable "name" {
  type = string
}
variable "vpc_id" {
  type = string
}
variable "kms_key_arn" {
  type = string
}
variable "retention_days" {
  type = number
}

resource "aws_cloudwatch_log_group" "vpc_flow" {
  name              = "/${var.name}/vpc-flow"
  retention_in_days = var.retention_days
  kms_key_id        = var.kms_key_arn
}
resource "aws_cloudwatch_log_group" "msk" {
  name              = "/${var.name}/msk"
  retention_in_days = var.retention_days
  kms_key_id        = var.kms_key_arn
}

data "aws_iam_policy_document" "flow_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["vpc-flow-logs.amazonaws.com"]
    }

  }
}
resource "aws_iam_role" "flow" {
  name               = "${var.name}-vpc-flow"
  assume_role_policy = data.aws_iam_policy_document.flow_assume.json
}
resource "aws_iam_role_policy" "flow" {
  name = "${var.name}-vpc-flow"
  role = aws_iam_role.flow.id
  policy = jsonencode({
    Version = "2012-10-17", Statement = [{
      Effect = "Allow", Action = ["logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogGroups", "logs:DescribeLogStreams"], Resource = "${aws_cloudwatch_log_group.vpc_flow.arn}:*"
    }]
  })
}
resource "aws_flow_log" "this" {
  iam_role_arn    = aws_iam_role.flow.arn
  log_destination = aws_cloudwatch_log_group.vpc_flow.arn
  traffic_type    = "ALL"
  vpc_id          = var.vpc_id
}

output "msk_log_group_name" {
  value = aws_cloudwatch_log_group.msk.name
}
