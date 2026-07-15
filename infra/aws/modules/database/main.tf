variable "name" {
  type = string
}
variable "subnet_ids" {
  type = list(string)
}
variable "security_group_id" {
  type = string
}
variable "kms_key_arn" {
  type = string
}
variable "instance_class" {
  type = string
}
variable "allocated_storage_gb" {
  type = number
}
variable "backup_retention_days" {
  type = number
}
variable "multi_az" {
  type = bool
}
variable "deletion_protection" {
  type = bool
}
variable "performance_insights_enabled" {
  type = bool
}
variable "skip_final_snapshot" {
  type = bool
}

resource "aws_db_subnet_group" "this" {
  name       = "${var.name}-database"
  subnet_ids = var.subnet_ids
}
resource "aws_db_parameter_group" "this" {
  name   = "${var.name}-ledger"
  family = "postgres16"
  parameter {
    name  = "log_statement"
    value = "all"
  }
  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }
}
data "aws_iam_policy_document" "monitoring_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["monitoring.rds.amazonaws.com"]
    }
  }
}
resource "aws_iam_role" "monitoring" {
  name               = "${var.name}-rds-monitoring"
  assume_role_policy = data.aws_iam_policy_document.monitoring_assume.json
}
resource "aws_iam_role_policy_attachment" "monitoring" {
  role       = aws_iam_role.monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

resource "aws_db_instance" "this" {
  identifier                          = "${var.name}-ledger"
  engine                              = "postgres"
  engine_version                      = "16"
  instance_class                      = var.instance_class
  allocated_storage                   = var.allocated_storage_gb
  storage_encrypted                   = true
  kms_key_id                          = var.kms_key_arn
  db_subnet_group_name                = aws_db_subnet_group.this.name
  vpc_security_group_ids              = [var.security_group_id]
  backup_retention_period             = var.backup_retention_days
  deletion_protection                 = var.deletion_protection
  skip_final_snapshot                 = var.skip_final_snapshot
  multi_az                            = var.multi_az
  username                            = "voicesecure_admin"
  manage_master_user_password         = true
  iam_database_authentication_enabled = true
  performance_insights_enabled        = var.performance_insights_enabled
  performance_insights_kms_key_id     = var.performance_insights_enabled ? var.kms_key_arn : null
  enabled_cloudwatch_logs_exports     = ["postgresql", "upgrade"]
  monitoring_interval                 = 60
  monitoring_role_arn                 = aws_iam_role.monitoring.arn
  auto_minor_version_upgrade          = true
  copy_tags_to_snapshot               = true
  parameter_group_name                = aws_db_parameter_group.this.name
}
output "endpoint" {
  value = aws_db_instance.this.endpoint
}
output "master_secret_arn" {
  value = try(aws_db_instance.this.master_user_secret[0].secret_arn, null)
}
