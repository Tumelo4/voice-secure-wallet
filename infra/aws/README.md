# Reusable AWS infrastructure

The AWS baseline is split into capability modules and explicit environment
compositions. Modules contain resource mechanics; environments own cost,
availability, retention, exposure, and deletion decisions.

## Layout

- `modules/`: networking, encryption, database, cache, messaging,
  audit-storage, and observability capabilities.
- `environments/demo/`: temporary engineering environment using local state,
  two private subnets, single-AZ/single-node data services, smaller instances,
  short retention, no public edge, and no object lock.
- `environments/production-reference/`: hardened reference using remote state,
  three private subnets, Multi-AZ RDS, HA Redis, three-broker MSK, IAM broker
  authentication, 365-day logs, deletion protection, and compliance object
  lock.
- `bootstrap/`: independently applied remote-state bucket and DynamoDB locking.

The demo is cheaper, not insecure: encryption in transit and at rest, private
subnets, restricted security groups, KMS, flow logs, IAM-authenticated MSK, and
public-access blocks remain enabled. It deliberately relaxes availability,
retention, and deletion protections that make production expensive and durable.

## Order of operations

1. Apply `bootstrap/` once from an approved administrator session.
2. Initialize `environments/production-reference/` against that backend.
3. Supply `TF_VAR_redis_auth_token` through an approved secret broker.
4. Plan and review before apply. The guarded deployment script also verifies
   the authenticated AWS account.

No environment is applied by repository validation. Run
`scripts/test-terraform-aws-baseline.sh` to format, initialize without backends,
and validate bootstrap, demo, and production-reference independently.
