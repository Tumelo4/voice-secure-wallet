# Staging application delivery

The staging delivery path uses one Terraform-managed EC2 host for the Java modular monolith. Terraform owns the application infrastructure; GitHub Actions publishes an immutable image and deploys it through Systems Manager.

## Deployment boundary

- `api-adapter-service` is the single Java deployable and includes the ledger, payment, identity, wallet, beneficiary, fraud, compliance, notification, support, recovery, operations, and launch modules.
- `voice-service` remains a separate, non-deployed runtime until its independent biometric validation and durable production dependencies are complete.
- RDS, ElastiCache, and MSK remain disabled in the low-cost staging profile.

## Terraform ownership

- `public_edge.tf` owns the internet gateway, public subnet, route, and application security group.
- `container_registry.tf` owns the immutable ECR repository and image-retention policy.
- `application_host_iam.tf` owns the EC2 trust policy, SSM access, repository-scoped ECR pull access, and instance profile.
- `application_host.tf` owns the single micro EC2 runtime and its encrypted root volume.
- `deployment_outputs.tf` exposes the stable deployment contract consumed by GitHub Actions.

Terraform creates missing resources and reconciles existing resources. The workflow must not create infrastructure with ad-hoc `aws ... create-*` commands.

## Delivery workflow

`.github/workflows/staging-application-delivery.yml`:

1. Resolves the exact commit covered by the staging plan.
2. Waits for quality, security, capability, and container-supply-chain gates for that commit.
3. Assumes the repository-scoped AWS role through GitHub OIDC.
4. Applies the saved staging Terraform plan through the guarded deployment script.
5. Reads the EC2 and ECR targets from Terraform outputs.
6. Reuses an existing immutable image for the commit or builds and pushes it once.
7. Uses SSM Run Command to deploy the container with rollback and health checks.
8. Verifies the public liveness and readiness endpoints.

The application currently runs with `VSW_ENVIRONMENT=demo`. This accurately reflects the in-memory persistence used by non-production runtime composition; restarting the container resets application data.

## Bootstrap requirement

Apply `infra/aws/bootstrap` once with an approved administrative identity before GitHub Actions can use the remote backend or OIDC role. The bootstrap trust policy accepts only:

```text
repo:Tumelo4/voice-secure-wallet:ref:refs/heads/main
repo:Tumelo4/voice-secure-wallet:environment:staging
```

The bootstrap also grants the GitHub role scoped access to create and manage the staging ECR repository, public edge, tagged EC2 host, application instance role/profile, and SSM deployment command.

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
- Interface VPC endpoints remain disabled by default

Public IPv4, KMS, storage, logging, and data transfer can still incur charges. Use AWS Budgets and billing alerts rather than assuming every staging resource is permanently free.
