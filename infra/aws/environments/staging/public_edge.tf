data "aws_availability_zones" "staging" {
  state = "available"
}

resource "aws_internet_gateway" "staging" {
  vpc_id = module.networking.vpc_id

  tags = {
    Name        = "${var.name}-internet-gateway"
    Project     = "voice-secure-wallet"
    Component   = "public-edge"
    Environment = "staging"
    ManagedBy   = "terraform"
    CostProfile = "free-tier"
  }
}

resource "aws_subnet" "application_public" {
  # checkov:skip=CKV_AWS_130:This dedicated staging edge subnet intentionally assigns public addresses to the single public application host.
  vpc_id                  = module.networking.vpc_id
  cidr_block              = var.application_public_subnet_cidr
  availability_zone       = data.aws_availability_zones.staging.names[0]
  map_public_ip_on_launch = true

  tags = {
    Name        = "${var.name}-application-public"
    Project     = "voice-secure-wallet"
    Component   = "application-runtime"
    Environment = "staging"
    ManagedBy   = "terraform"
    CostProfile = "free-tier"
  }
}

resource "aws_route_table" "application_public" {
  vpc_id = module.networking.vpc_id

  tags = {
    Name        = "${var.name}-application-public"
    Project     = "voice-secure-wallet"
    Component   = "public-edge"
    Environment = "staging"
    ManagedBy   = "terraform"
    CostProfile = "free-tier"
  }
}

resource "aws_route" "application_internet" {
  route_table_id         = aws_route_table.application_public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.staging.id
}

resource "aws_route_table_association" "application_public" {
  subnet_id      = aws_subnet.application_public.id
  route_table_id = aws_route_table.application_public.id
}

resource "aws_security_group" "application_host" {
  name                   = "${var.name}-application-host"
  description            = "HTTP ingress for the staging application runtime"
  vpc_id                 = module.networking.vpc_id
  revoke_rules_on_delete = true

  tags = {
    Name        = "${var.name}-application-host"
    Project     = "voice-secure-wallet"
    Component   = "application-runtime"
    Environment = "staging"
    ManagedBy   = "terraform"
    CostProfile = "free-tier"
  }
}

resource "aws_vpc_security_group_ingress_rule" "application_http" {
  # checkov:skip=CKV_AWS_260:Port 80 is intentionally public for the temporary staging API and health checks; production uses the private application tier behind an ALB.
  for_each = var.application_ingress_cidrs

  security_group_id = aws_security_group.application_host.id
  description       = "Staging API and health-check traffic from ${each.value}"
  cidr_ipv4         = each.value
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"

  tags = {
    Project     = "voice-secure-wallet"
    Component   = "public-edge"
    Environment = "staging"
    ManagedBy   = "terraform"
    CostProfile = "free-tier"
  }
}

resource "aws_vpc_security_group_egress_rule" "application_outbound" {
  security_group_id = aws_security_group.application_host.id
  description       = "Outbound access for package installation, ECR, and Systems Manager"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"

  tags = {
    Project     = "voice-secure-wallet"
    Component   = "application-runtime"
    Environment = "staging"
    ManagedBy   = "terraform"
    CostProfile = "free-tier"
  }
}
