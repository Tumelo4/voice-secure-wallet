terraform {
  backend "s3" {
    bucket               = "voicesecure-terraform-state"
    key                  = "environments/voicesecure.tfstate"
    region               = "af-south-1"
    dynamodb_table       = "voicesecure-terraform-locks"
    encrypt              = true
    workspace_key_prefix = "voicesecure"
  }
}
