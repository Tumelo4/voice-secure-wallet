data "aws_ami" "application_host" {
  count       = var.application_ami_id == null ? 1 : 0
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }

  filter {
    name   = "root-device-type"
    values = ["ebs"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

locals {
  application_ami_id = var.application_ami_id != null ? var.application_ami_id : data.aws_ami.application_host[0].id
}

resource "aws_instance" "application_host" {
  # checkov:skip=CKV_AWS_88:The low-cost staging runtime is intentionally public because this environment has no NAT gateway or load balancer.
  ami                                  = local.application_ami_id
  instance_type                        = var.application_instance_type
  subnet_id                            = aws_subnet.application_public.id
  vpc_security_group_ids               = [aws_security_group.application_host.id]
  iam_instance_profile                 = aws_iam_instance_profile.application_host.name
  associate_public_ip_address          = true
  monitoring                           = false
  ebs_optimized                        = true
  source_dest_check                    = true
  instance_initiated_shutdown_behavior = "stop"
  user_data_replace_on_change          = true

  credit_specification {
    cpu_credits = "standard"
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
    instance_metadata_tags      = "disabled"
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.application_root_volume_size_gb
    encrypted             = true
    delete_on_termination = true
  }

  volume_tags = {
    Name        = "${var.name}-application-host-root"
    Project     = "voice-secure-wallet"
    Component   = "application-runtime"
    Environment = "staging"
    ManagedBy   = "terraform"
    CostProfile = "free-tier"
  }

  user_data = <<-USER_DATA
    #!/usr/bin/env bash
    set -Eeuo pipefail

    exec > >(tee /var/log/voicesecure-bootstrap.log | logger -t voicesecure-bootstrap -s 2>/dev/console) 2>&1

    dnf install -y docker curl
    systemctl enable --now docker
    systemctl enable --now amazon-ssm-agent
    usermod -aG docker ec2-user

    install -d -m 0755 /var/lib/voicesecure
    touch /var/lib/voicesecure/bootstrap-complete
  USER_DATA

  tags = {
    Name        = "${var.name}-application-host"
    Project     = "voice-secure-wallet"
    Component   = "application-runtime"
    Environment = "staging"
    ManagedBy   = "terraform"
    CostProfile = "free-tier"
  }

  lifecycle {
    # New Amazon Linux images are discovered for replacement hosts, but routine
    # application delivery must not replace a healthy instance just because a
    # newer AMI was published. Replace deliberately to adopt a new base image.
    ignore_changes = [ami]
  }

  depends_on = [
    aws_route.application_internet,
    aws_route_table_association.application_public,
    aws_iam_role_policy_attachment.application_host_ssm,
    aws_iam_role_policy.application_host_ecr_pull
  ]
}
