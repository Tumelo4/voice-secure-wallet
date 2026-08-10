# Staging application delivery

The staging delivery path has two integrated workflows. The backend uses one Terraform-managed EC2 host for the Java modular monolith. The frontend is an Expo web export stored in a private S3 bucket. CloudFront provides the single HTTPS origin and routes API paths to the backend.

## Deployment boundary

- `api-adapter-service` is the single Java deployable and includes the ledger, payment, identity, wallet, beneficiary, fraud, compliance, notification, support, recovery, operations, and launch modules.
- `voice-service` remains a separate, non-deployed runtime until its independent biometric validation and durable production dependencies are complete.
- RDS, ElastiCache, and MSK remain disabled in the low-cost staging profile.

## Terraform ownership

- `public_edge.tf` owns the internet gateway, public subnet, route, and application security group.
- `container_registry.tf` owns the immutable ECR repository and image-retention policy.
- `application_host_iam.tf` owns the EC2 trust policy, SSM access, repository-scoped ECR pull access, and instance profile.
- `application_host.tf` owns the single micro EC2 runtime and its encrypted root volume.
- `frontend_delivery.tf` owns the private, encrypted S3 bucket, CloudFront origin access control, HTTPS distribution, security headers, and API routing.
- `deployment_outputs.tf` exposes the stable deployment contract consumed by GitHub Actions.

Terraform creates missing resources and reconciles existing resources. The workflow must not create infrastructure with ad-hoc `aws ... create-*` commands.

## Backend workflow

`.github/workflows/staging-application-delivery.yml`:

1. Resolves the exact `main` commit that passed the `Security gates` workflow.
2. Refuses failed, cancelled, non-push, or non-`main` security runs.
3. Assumes the repository-scoped AWS role through GitHub OIDC.
4. Applies the saved staging Terraform plan through the guarded deployment script.
5. Reads the EC2 and ECR targets from Terraform outputs.
6. Reuses an existing immutable image for the commit or builds and pushes it once.
7. Uses SSM Run Command to deploy the container with rollback and health checks.
8. Verifies liveness and readiness through the HTTPS CloudFront edge.

## Frontend workflow

`.github/workflows/staging-frontend-deployment.yml` runs only after a successful
`Staging backend deployment` for the same `main` commit (or by an explicit
manual dispatch). It reads the backend/frontend contract from Terraform state,
tests and exports the Expo web app with `EXPO_PUBLIC_API_BASE_URL` set to the
shared HTTPS origin, publishes to the private S3 bucket, invalidates CloudFront,
and verifies the frontend plus `/health/ready` integration.

The EC2 security group accepts port 80 only from the AWS-managed CloudFront
origin-facing prefix list. Users cannot bypass HTTPS or connect directly to the
API from the public internet.

The application currently runs with `VSW_ENVIRONMENT=demo`. This accurately reflects the in-memory persistence used by non-production runtime composition; restarting the container resets application data.

## Bootstrap requirement

Apply `infra/aws/bootstrap` once with an approved administrative identity before GitHub Actions can use the remote backend or OIDC role. The bootstrap trust policy accepts only:

```text
repo:Tumelo4/voice-secure-wallet:ref:refs/heads/main
repo:Tumelo4/voice-secure-wallet:environment:staging
```

The bootstrap also grants the GitHub role scoped access to create and manage the staging ECR repository, private frontend bucket, CloudFront edge, tagged EC2 host, application instance role/profile, and SSM deployment command.

## Required GitHub configuration

- Environment: `staging`
- Deployment branch restriction: `main`
- Environment secret: `TF_VAR_REDIS_AUTH_TOKEN`

The Redis token remains required by the shared staging configuration even while Redis creation is disabled.

## Cost controls

- One micro EC2 instance with standard CPU credits
- One 8 GiB encrypted gp3 root volume
- No load balancer, NAT gateway, ECS/Fargate, RDS, ElastiCache, or MSK
- ECR retains only five images
- Private versioned S3 frontend storage and a PriceClass_100 CloudFront distribution
- Interface VPC endpoints remain disabled by default

Public IPv4, KMS, storage, logging, and data transfer can still incur charges. Use AWS Budgets and billing alerts rather than assuming every staging resource is permanently free.
