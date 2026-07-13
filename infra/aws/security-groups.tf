resource "aws_security_group" "alb" {
  #checkov:skip=CKV2_AWS_5:The ALB is supplied by the environment deployment stack and attaches this exported edge group.
  name                   = "voicesecure-${var.environment}-alb"
  description            = "Public HTTPS ingress for the API edge"
  vpc_id                 = aws_vpc.voice_secure.id
  revoke_rules_on_delete = true

  ingress = []
  egress  = []
}

resource "aws_vpc_security_group_ingress_rule" "alb_https" {
  description       = "Public TLS ingress to the application load balancer"
  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_app" {
  description                  = "Forward API traffic from the load balancer to the application tier"
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = var.app_port
  to_port                      = var.app_port
  ip_protocol                  = "tcp"
}

resource "aws_security_group" "app" {
  #checkov:skip=CKV2_AWS_5:The application runtime is supplied by the environment deployment stack and attaches this exported group.
  name                   = "voicesecure-${var.environment}-app"
  description            = "Private application tier"
  vpc_id                 = aws_vpc.voice_secure.id
  revoke_rules_on_delete = true

  ingress = []
  egress  = []
}

resource "aws_vpc_security_group_ingress_rule" "app_from_alb" {
  description                  = "Accept API traffic only from the load balancer"
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.alb.id
  from_port                    = var.app_port
  to_port                      = var.app_port
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "app_to_database" {
  description                  = "Allow application PostgreSQL connections"
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.database.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "app_to_redis" {
  description                  = "Allow application Redis TLS connections"
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.redis.id
  from_port                    = 6379
  to_port                      = 6379
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "app_to_msk" {
  description                  = "Allow application IAM-authenticated MSK connections"
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.msk.id
  from_port                    = 9098
  to_port                      = 9098
  ip_protocol                  = "tcp"
}

resource "aws_security_group" "msk" {
  name                   = "voicesecure-${var.environment}-msk"
  description            = "MSK broker access from private workloads"
  vpc_id                 = aws_vpc.voice_secure.id
  revoke_rules_on_delete = true

  ingress = []
  egress  = []
}

resource "aws_vpc_security_group_ingress_rule" "msk_from_app" {
  description                  = "Accept MSK traffic only from the application tier"
  security_group_id            = aws_security_group.msk.id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 9098
  to_port                      = 9098
  ip_protocol                  = "tcp"
}

resource "aws_security_group" "database" {
  name                   = "voicesecure-${var.environment}-database"
  description            = "PostgreSQL access from private workloads"
  vpc_id                 = aws_vpc.voice_secure.id
  revoke_rules_on_delete = true

  ingress = []
  egress  = []
}

resource "aws_vpc_security_group_ingress_rule" "database_from_app" {
  description                  = "Accept PostgreSQL traffic only from the application tier"
  security_group_id            = aws_security_group.database.id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}

resource "aws_security_group" "redis" {
  name                   = "voicesecure-${var.environment}-redis"
  description            = "Redis access from private workloads"
  vpc_id                 = aws_vpc.voice_secure.id
  revoke_rules_on_delete = true

  ingress = []
  egress  = []
}

resource "aws_vpc_security_group_ingress_rule" "redis_from_app" {
  description                  = "Accept Redis traffic only from the application tier"
  security_group_id            = aws_security_group.redis.id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 6379
  to_port                      = 6379
  ip_protocol                  = "tcp"
}
