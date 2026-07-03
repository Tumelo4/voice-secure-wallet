data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "voice_secure" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true
}

resource "aws_subnet" "private" {
  for_each = {
    for index, cidr in var.private_subnet_cidrs : tostring(index) => cidr
  }

  vpc_id                  = aws_vpc.voice_secure.id
  cidr_block              = each.value
  availability_zone       = data.aws_availability_zones.available.names[tonumber(each.key)]
  map_public_ip_on_launch = false
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.voice_secure.id
}

resource "aws_route_table_association" "private" {
  for_each = aws_subnet.private

  subnet_id      = each.value.id
  route_table_id = aws_route_table.private.id
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.voice_secure.id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.private.id]
}

resource "aws_db_subnet_group" "private" {
  name       = "voicesecure-${var.environment}-database"
  subnet_ids = [for subnet in aws_subnet.private : subnet.id]
}

resource "aws_elasticache_subnet_group" "private" {
  name       = "voicesecure-${var.environment}-redis"
  subnet_ids = [for subnet in aws_subnet.private : subnet.id]
}
