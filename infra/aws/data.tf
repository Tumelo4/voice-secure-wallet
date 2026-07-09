resource "aws_msk_configuration" "events" {
  name           = "voicesecure-${var.environment}-events"
  kafka_versions = ["3.6.0"]

  server_properties = <<-PROPERTIES
auto.create.topics.enable=false
default.replication.factor=3
min.insync.replicas=2
unclean.leader.election.enable=false
  PROPERTIES
}

resource "aws_msk_cluster" "events" {
  cluster_name           = "voicesecure-${var.environment}-events"
  kafka_version          = "3.6.0"
  number_of_broker_nodes = var.msk_broker_count

  broker_node_group_info {
    instance_type   = var.msk_instance_type
    client_subnets  = [for subnet in aws_subnet.private : subnet.id]
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = 200
      }
    }
  }

  encryption_info {
    encryption_at_rest_kms_key_arn = aws_kms_key.platform.arn

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
    arn      = aws_msk_configuration.events.arn
    revision = aws_msk_configuration.events.latest_revision
  }
}

resource "aws_db_instance" "ledger" {
  identifier                  = "voicesecure-${var.environment}-ledger"
  engine                      = "postgres"
  engine_version              = "16"
  instance_class              = var.rds_instance_class
  allocated_storage           = var.rds_allocated_storage_gb
  storage_encrypted           = true
  kms_key_id                  = aws_kms_key.platform.arn
  db_subnet_group_name        = aws_db_subnet_group.private.name
  vpc_security_group_ids      = [aws_security_group.database.id]
  backup_retention_period     = var.rds_backup_retention_days
  deletion_protection         = true
  multi_az                    = true
  username                    = "voicesecure_admin"
  manage_master_user_password = true
}

resource "aws_elasticache_replication_group" "api_rate_limits" {
  replication_group_id       = "voicesecure-${var.environment}-rate-limits"
  description                = "VoiceSecure distributed API rate-limit cache"
  engine                     = "redis"
  node_type                  = var.redis_node_type
  num_cache_clusters         = 2
  automatic_failover_enabled = true
  multi_az_enabled           = true
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  kms_key_id                 = aws_kms_key.platform.arn
  subnet_group_name          = aws_elasticache_subnet_group.private.name
  security_group_ids         = [aws_security_group.redis.id]
}
