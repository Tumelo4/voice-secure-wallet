variable "lock_table_name" {
  type    = string
  default = "voicesecure-terraform-locks"
}
resource "aws_dynamodb_table" "locking" {
  name         = var.lock_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"
  attribute {
    name = "LockID"
    type = "S"
  }
  server_side_encryption {
    enabled = true
  }
  point_in_time_recovery {
    enabled = true
  }
  lifecycle {
    prevent_destroy = true
  }
}
output "lock_table" {
  value = aws_dynamodb_table.locking.name
}
