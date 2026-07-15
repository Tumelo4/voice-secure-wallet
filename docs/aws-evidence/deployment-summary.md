# AWS Deployment Evidence

Environment: Temporary demonstration  
Region: af-south-1  
Provisioned through: Terraform  
Validated services: Not yet validated in a live AWS account  
Live integration tests: Not yet run  
Resources destroyed after validation: Not yet applicable

Update this page only after attaching the corresponding plan, validation, and teardown evidence. Repository validation proves Terraform structure; it does not prove deployment.

## Demonstration stages

1. Foundation: VPC, private subnets, security groups, KMS, encrypted S3, IAM dependencies, CloudWatch logs, and VPC Flow Logs.
2. Data services: temporarily enable RDS and Redis, capture connectivity/idempotency evidence, then disable them.
3. MSK: enable last, capture event-flow and lag evidence, then disable immediately.

## Résumé wording

Before live deployment: “Designed a secure AWS infrastructure baseline using Terraform, including private VPC networking, KMS, Amazon MSK, RDS PostgreSQL, ElastiCache and encrypted S3 audit storage.”

Use deployed-and-validated wording only after this evidence pack contains successful live results and teardown proof.
