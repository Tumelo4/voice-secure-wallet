terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
  backend "s3" {
    bucket         = "voicesecure-terraform-state"
    key            = "environments/production-reference.tfstate"
    region         = "af-south-1"
    dynamodb_table = "voicesecure-terraform-locks"
    encrypt        = true

  }
}
provider "aws" {
  region = var.aws_region
  default_tags {
    tags = {
      Environment        = "production-reference"
      ManagedBy          = "terraform"
      DataClassification = "confidential"
    }
  }
}

module "encryption" {
  source               = "../../modules/encryption"
  name                 = var.name
  deletion_window_days = 30
  enable_rotation      = true
}
module "networking" {
  source                      = "../../modules/networking"
  name                        = var.name
  vpc_cidr                    = "10.40.0.0/16"
  private_subnet_cidrs        = ["10.40.10.0/24", "10.40.20.0/24", "10.40.30.0/24"]
  app_port                    = 8080
  allow_public_ingress        = true
  interface_endpoint_services = var.interface_endpoint_services
}
module "observability" {
  source         = "../../modules/observability"
  name           = var.name
  vpc_id         = module.networking.vpc_id
  kms_key_arn    = module.encryption.key_arn
  retention_days = var.log_retention_days
}
module "database" {
  count                        = var.enable_rds ? 1 : 0
  source                       = "../../modules/database"
  name                         = var.name
  subnet_ids                   = module.networking.private_subnet_ids
  security_group_id            = module.networking.database_security_group_id
  kms_key_arn                  = module.encryption.key_arn
  instance_class               = "db.m7g.large"
  allocated_storage_gb         = 100
  backup_retention_days        = 35
  multi_az                     = var.rds_multi_az
  deletion_protection          = var.rds_deletion_protection
  performance_insights_enabled = var.rds_performance_insights_enabled
  skip_final_snapshot          = false
}
module "cache" {
  count             = var.enable_redis ? 1 : 0
  source            = "../../modules/cache"
  name              = var.name
  subnet_ids        = module.networking.private_subnet_ids
  security_group_id = module.networking.redis_security_group_id
  kms_key_arn       = module.encryption.key_arn
  node_type         = "cache.m7g.large"
  node_count        = var.redis_node_count
  multi_az          = var.redis_multi_az
  auth_token        = var.redis_auth_token
}
module "messaging" {
  count                = var.enable_msk ? 1 : 0
  source               = "../../modules/messaging"
  name                 = var.name
  subnet_ids           = module.networking.private_subnet_ids
  security_group_id    = module.networking.msk_security_group_id
  kms_key_arn          = module.encryption.key_arn
  log_group_name       = module.observability.msk_log_group_name
  broker_count         = 3
  instance_type        = "kafka.m7g.large"
  volume_size_gb       = 200
  publisher_role_names = ["payment", "ledger"]
}
module "audit_storage" {
  source              = "../../modules/audit-storage"
  name                = var.name
  kms_key_arn         = module.encryption.key_arn
  object_lock_enabled = var.audit_object_lock_enabled
  retention_days      = 365
}

output "database_endpoint" {
  value = try(module.database[0].endpoint, null)
}
output "msk_bootstrap_brokers_sasl_iam" {
  value = try(module.messaging[0].bootstrap_brokers_sasl_iam, null)
}
output "redis_primary_endpoint" {
  value = try(module.cache[0].primary_endpoint, null)
}
output "audit_bucket" {
  value = module.audit_storage.bucket_name
}
output "publisher_role_arns" {
  value = try(module.messaging[0].publisher_role_arns, {})
}
output "interface_endpoint_ids" {
  value = module.networking.interface_endpoint_ids
}
