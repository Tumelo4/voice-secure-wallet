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
  source               = "../../modules/networking"
  name                 = var.name
  vpc_cidr             = "10.40.0.0/16"
  private_subnet_cidrs = ["10.40.10.0/24", "10.40.20.0/24", "10.40.30.0/24"]
  app_port             = 8080
  allow_public_ingress = true
}
module "observability" {
  source         = "../../modules/observability"
  name           = var.name
  vpc_id         = module.networking.vpc_id
  kms_key_arn    = module.encryption.key_arn
  retention_days = 365
}
module "database" {
  source                = "../../modules/database"
  name                  = var.name
  subnet_ids            = module.networking.private_subnet_ids
  security_group_id     = module.networking.database_security_group_id
  kms_key_arn           = module.encryption.key_arn
  instance_class        = "db.m7g.large"
  allocated_storage_gb  = 100
  backup_retention_days = 35
  multi_az              = true
  deletion_protection   = true
  skip_final_snapshot   = false
}
module "cache" {
  source            = "../../modules/cache"
  name              = var.name
  subnet_ids        = module.networking.private_subnet_ids
  security_group_id = module.networking.redis_security_group_id
  kms_key_arn       = module.encryption.key_arn
  node_type         = "cache.m7g.large"
  node_count        = 2
  auth_token        = var.redis_auth_token
}
module "messaging" {
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
  object_lock_enabled = true
  retention_days      = 365
}

output "database_endpoint" {
  value = module.database.endpoint
}
output "msk_bootstrap_brokers_sasl_iam" {
  value = module.messaging.bootstrap_brokers_sasl_iam
}
output "redis_primary_endpoint" {
  value = module.cache.primary_endpoint
}
output "audit_bucket" {
  value = module.audit_storage.bucket_name
}
output "publisher_role_arns" {
  value = module.messaging.publisher_role_arns
}
