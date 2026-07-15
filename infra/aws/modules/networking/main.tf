variable "name" {
  type = string
}
variable "vpc_cidr" {
  type = string
}
variable "private_subnet_cidrs" {
  type = list(string)
}
variable "app_port" {
  type = number
}
variable "allow_public_ingress" {
  type = bool
}

data "aws_availability_zones" "available" {
  state = "available"
}
data "aws_region" "current" {}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true
  tags = {
    Name = var.name
  }
}

resource "aws_default_security_group" "restricted" {
  vpc_id  = aws_vpc.this.id
  ingress = []
  egress  = []
}

resource "aws_subnet" "private" {
  for_each = {
    for index, cidr in var.private_subnet_cidrs : tostring(index) => cidr
  }
  vpc_id                  = aws_vpc.this.id
  cidr_block              = each.value
  availability_zone       = data.aws_availability_zones.available.names[tonumber(each.key)]
  map_public_ip_on_launch = false
  tags = {
    Name = "${var.name}-private-${each.key}"
  }
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.this.id
}
resource "aws_route_table_association" "private" {
  for_each       = aws_subnet.private
  subnet_id      = each.value.id
  route_table_id = aws_route_table.private.id
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${data.aws_region.current.name}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.private.id]
}

resource "aws_security_group" "alb" {
  name                   = "${var.name}-alb"
  description            = "HTTPS edge"
  vpc_id                 = aws_vpc.this.id
  revoke_rules_on_delete = true
  ingress                = []
  egress                 = []
}
resource "aws_security_group" "app" {
  name                   = "${var.name}-app"
  description            = "Private application tier"
  vpc_id                 = aws_vpc.this.id
  revoke_rules_on_delete = true
  ingress                = []
  egress                 = []
}
resource "aws_security_group" "database" {
  name                   = "${var.name}-database"
  description            = "PostgreSQL"
  vpc_id                 = aws_vpc.this.id
  revoke_rules_on_delete = true
  ingress                = []
  egress                 = []
}
resource "aws_security_group" "redis" {
  name                   = "${var.name}-redis"
  description            = "Redis TLS"
  vpc_id                 = aws_vpc.this.id
  revoke_rules_on_delete = true
  ingress                = []
  egress                 = []
}
resource "aws_security_group" "msk" {
  name                   = "${var.name}-msk"
  description            = "MSK IAM TLS"
  vpc_id                 = aws_vpc.this.id
  revoke_rules_on_delete = true
  ingress                = []
  egress                 = []
}

resource "aws_vpc_security_group_ingress_rule" "alb_https" {
  count             = var.allow_public_ingress ? 1 : 0
  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}
resource "aws_vpc_security_group_ingress_rule" "app_from_alb" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.alb.id
  from_port                    = var.app_port
  to_port                      = var.app_port
  ip_protocol                  = "tcp"
}
resource "aws_vpc_security_group_egress_rule" "alb_to_app" {
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = var.app_port
  to_port                      = var.app_port
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "app_to_database" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.database.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}
resource "aws_vpc_security_group_ingress_rule" "database_from_app" {
  security_group_id            = aws_security_group.database.id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}
resource "aws_vpc_security_group_egress_rule" "app_to_redis" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.redis.id
  from_port                    = 6379
  to_port                      = 6379
  ip_protocol                  = "tcp"
}
resource "aws_vpc_security_group_ingress_rule" "redis_from_app" {
  security_group_id            = aws_security_group.redis.id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 6379
  to_port                      = 6379
  ip_protocol                  = "tcp"
}
resource "aws_vpc_security_group_egress_rule" "app_to_msk" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.msk.id
  from_port                    = 9098
  to_port                      = 9098
  ip_protocol                  = "tcp"
}
resource "aws_vpc_security_group_ingress_rule" "msk_from_app" {
  security_group_id            = aws_security_group.msk.id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 9098
  to_port                      = 9098
  ip_protocol                  = "tcp"
}

output "vpc_id" {
  value = aws_vpc.this.id
}
output "private_subnet_ids" {
  value = [for subnet in aws_subnet.private : subnet.id]
}
output "app_security_group_id" {
  value = aws_security_group.app.id
}
output "database_security_group_id" {
  value = aws_security_group.database.id
}
output "redis_security_group_id" {
  value = aws_security_group.redis.id
}
output "msk_security_group_id" {
  value = aws_security_group.msk.id
}
