terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}
provider "aws" {
  region = var.aws_region
  default_tags {
    tags = {
      Environment = "demo"
      ManagedBy   = "terraform"
      Temporary   = "true"
    }
  }
}

module "encryption" {
  source               = "../../modules/encryption"
  name                 = var.name
  deletion_window_days = 7
  enable_rotation      = true
}
module "networking" {
  source                      = "../../modules/networking"
  name                        = var.name
  vpc_cidr                    = "10.50.0.0/16"
  private_subnet_cidrs        = ["10.50.10.0/24", "10.50.20.0/24"]
  app_port                    = 8080
  allow_public_ingress        = false
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
  instance_class               = "db.t4g.small"
  allocated_storage_gb         = 20
  backup_retention_days        = 1
  multi_az                     = var.rds_multi_az
  deletion_protection          = var.rds_deletion_protection
  performance_insights_enabled = var.rds_performance_insights_enabled
  skip_final_snapshot          = true
}
module "cache" {
  count             = var.enable_redis ? 1 : 0
  source            = "../../modules/cache"
  name              = var.name
  subnet_ids        = module.networking.private_subnet_ids
  security_group_id = module.networking.redis_security_group_id
  kms_key_arn       = module.encryption.key_arn
  node_type         = "cache.t4g.micro"
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
  broker_count         = 2
  instance_type        = "kafka.t3.small"
  volume_size_gb       = 20
  publisher_role_names = ["payment", "ledger"]
}
module "audit_storage" {
  source              = "../../modules/audit-storage"
  name                = var.name
  kms_key_arn         = module.encryption.key_arn
  object_lock_enabled = var.audit_object_lock_enabled
  retention_days      = 30
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
output "interface_endpoint_ids" {
  value = module.networking.interface_endpoint_ids
}
