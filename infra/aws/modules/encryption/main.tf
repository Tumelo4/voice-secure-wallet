variable "name" {
  type = string
}
variable "deletion_window_days" {
  type = number
}
variable "enable_rotation" {
  type = bool
}

data "aws_caller_identity" "current" {}

data "aws_iam_policy_document" "key" {
  statement {
    sid       = "AccountAdministration"
    actions   = ["kms:*"]
    resources = ["*"]
    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"]

    }

  }
}

resource "aws_kms_key" "this" {
  description             = "${var.name} platform encryption key"
  deletion_window_in_days = var.deletion_window_days
  enable_key_rotation     = var.enable_rotation
  policy                  = data.aws_iam_policy_document.key.json
}

resource "aws_kms_alias" "this" {
  name          = "alias/${var.name}-platform"
  target_key_id = aws_kms_key.this.key_id
}

output "key_arn" {
  value = aws_kms_key.this.arn
}
output "key_id" {
  value = aws_kms_key.this.key_id
}
