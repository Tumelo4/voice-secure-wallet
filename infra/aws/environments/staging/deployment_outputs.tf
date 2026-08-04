output "api_repository_name" {
  description = "ECR repository that stores immutable API deployment images."
  value       = aws_ecr_repository.api.name
}

output "api_repository_url" {
  description = "Fully qualified ECR repository URL used by the delivery workflow."
  value       = aws_ecr_repository.api.repository_url
}

output "application_instance_id" {
  description = "SSM-managed EC2 instance targeted by the staging delivery workflow."
  value       = aws_instance.application_host.id
}

output "application_private_ip" {
  description = "Private IPv4 address of the staging application host."
  value       = aws_instance.application_host.private_ip
}

output "application_public_ip" {
  description = "Public IPv4 address used for staging access and health verification."
  value       = aws_instance.application_host.public_ip
}

output "application_base_url" {
  description = "HTTP base URL for the low-cost staging deployment."
  value       = "http://${aws_instance.application_host.public_ip}"
}
