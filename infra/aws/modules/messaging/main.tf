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
variable "log_group_name" {
  type = string
}
variable "broker_count" {
  type = number
}
variable "instance_type" {
  type = string
}
variable "volume_size_gb" {
  type = number
}
variable "publisher_role_names" {
  type    = set(string)
  default = []
}

resource "aws_msk_configuration" "this" {
  name              = "${var.name}-events"
  kafka_versions    = ["3.6.0"]
  server_properties = <<-PROPERTIES
auto.create.topics.enable=false
default.replication.factor=${var.broker_count >= 3 ? 3 : 1}
min.insync.replicas=${var.broker_count >= 3 ? 2 : 1}
unclean.leader.election.enable=false
  PROPERTIES
}
resource "aws_msk_cluster" "this" {
  cluster_name           = "${var.name}-events"
  kafka_version          = "3.6.0"
  number_of_broker_nodes = var.broker_count
  broker_node_group_info {
    instance_type   = var.instance_type
    client_subnets  = var.subnet_ids
    security_groups = [var.security_group_id]
    storage_info {
      ebs_storage_info {
        volume_size = var.volume_size_gb
      }
    }

  }
  encryption_info {
    encryption_at_rest_kms_key_arn = var.kms_key_arn
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }

  }
  client_authentication {
    sasl {
      iam = true
    }
  }
  configuration_info {
    arn      = aws_msk_configuration.this.arn
    revision = aws_msk_configuration.this.latest_revision
  }
  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = var.log_group_name
      }
    }
  }
}

data "aws_iam_policy_document" "task_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}
resource "aws_iam_role" "publisher" {
  for_each           = var.publisher_role_names
  name               = "${var.name}-${each.value}"
  assume_role_policy = data.aws_iam_policy_document.task_assume.json
}
resource "aws_iam_role_policy" "publisher" {
  for_each = aws_iam_role.publisher
  name     = "${var.name}-${each.key}-msk-publish"
  role     = each.value.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow", Action = ["kafka-cluster:Connect", "kafka-cluster:DescribeCluster"], Resource = aws_msk_cluster.this.arn
      },
      {
        Effect = "Allow", Action = ["kafka-cluster:DescribeTopic", "kafka-cluster:WriteData"], Resource = "${replace(aws_msk_cluster.this.arn, ":cluster/", ":topic/")}/*"
      }
    ]

  })
}

output "cluster_arn" {
  value = aws_msk_cluster.this.arn
}
output "bootstrap_brokers_sasl_iam" {
  value = aws_msk_cluster.this.bootstrap_brokers_sasl_iam
}
output "publisher_role_arns" {
  value = {
    for name, role in aws_iam_role.publisher : name => role.arn
  }
}
