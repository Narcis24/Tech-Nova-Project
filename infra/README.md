# Tech Nova on AWS

The whole stack, defined in OpenTofu (the open-source Terraform fork; same .tf files, `tofu` instead of `terraform`), in the shared class account (eu-west-1). Every resource name starts
with `tech-nova-team-` and carries a `Team = tech-nova-team` tag.

```
Browser ──HTTPS──▶ CloudFront ──┬─ /            ─▶ S3 (Angular build, private)
                                └─ /api/*, /auth/* ─▶ Load balancer ─┬─▶ app (Spring Boot)      ─┐
                                   (CloudFront only)                 └─▶ auth-service (NestJS)  ─┤
                                                                                                 ├─▶ RDS Postgres
                                   execution-engine (Spring Boot) ◀── Kafka ──▶ app              │
                                        └─▶ Alpaca quotes                                        │
                                   one-off jobs: migrations, prices, fundamentals ───────────────┘
```

| Piece | AWS | Where |
|---|---|---|
| UI | S3 + CloudFront (HTTPS, deep links via a CloudFront Function) | `cdn.tf`, `spa-rewrite.js` |
| app, auth-service, execution-engine, Kafka | ECS Fargate services, found by DNS as `<name>.tech-nova-team.internal` | `services.tf`, `modules/ecs-service` |
| API entrance | Application Load Balancer: `/api/*` to the app, `/auth/*` to auth-service; accepts CloudFront only | `alb.tf` |
| Database | RDS Postgres 16, private subnets, password managed by AWS in Secrets Manager | `database.tf` |
| Secrets | JWT secret, internal API token, Alpaca keys in Secrets Manager | `secrets.tf` |
| Schema and data | One-off Fargate tasks: `migrations` (db/migrations), `prices`, `fundamentals` (Yahoo Finance) | `jobs.tf` |
| Images | ECR, tagged with the git commit | `ecr.tf` |
| Monitoring | CloudWatch alarms (emailed when `alert_email` is set) and a dashboard | `monitoring.tf` |

The network belongs to the instructor (default VPC, private subnets, VPC endpoints): OpenTofu only looks it
up (`network.tf`). The execution engine and the data loaders run in a public subnet with a public IP, only
so they can call Alpaca and Yahoo; nothing can connect to them from outside.

## Every terminal first

Needs OpenTofu 1.10 or newer (`tofu version`); the first time in a directory, `tofu init` (in `main/`:
`tofu init -backend-config=backend.hcl`).

```bash
source ~/tech-nova-env.sh     # AWS profile, region, and the Alpaca keys read from ../.env
cd infra/terraform/main
```

Without it, `tofu plan` shows the app secret being replaced (with empty Alpaca keys): do not apply that.

## Deploy a new version

```bash
git commit ...                              # the image tag is the commit; uncommitted changes are refused
infra/scripts/build-and-push.sh             # or only some: build-and-push.sh app execution-engine
# set image_tag in infra/terraform/main/terraform.tfvars to the tag it prints
tofu apply                                  # rolling deploy; a version that never gets healthy is rolled back
infra/scripts/run-task.sh migrations        # when db/migrations has new files
infra/scripts/deploy-ui.sh                  # when the UI changed
```

Refresh market data: `infra/scripts/run-task.sh prices` and `infra/scripts/run-task.sh fundamentals`.

## Between demos

```bash
infra/scripts/stack.sh pause      # services to 0, database stopped: about $0.70 a day instead of $3-4
infra/scripts/stack.sh resume     # about 10 minutes
infra/scripts/stack.sh status
```

AWS restarts a stopped database by itself after 7 days.

## Known differences from docker-compose

- No mail server: password reset emails are not sent.
- Kafka and the engine's decision journal keep their data on the task's own disk: a restart starts them empty.
- No seeded admin (V014 only seeds one when `TEST_ADMIN_PASSWORD` is set, locally).

## Tear it all down

```bash
tofu destroy                      # everything in main/, ECR images and the UI bucket included (~10 minutes)
```

The state bucket (`bootstrap/`) is kept on purpose; to remove it as well, delete `prevent_destroy` in
`bootstrap/main.tf`, then `cd ../bootstrap && tofu destroy` (empty the bucket's versions first).

Check nothing is left:

```bash
aws resourcegroupstaggingapi get-resources --tag-filters Key=Team,Values=tech-nova-team \
  --query 'ResourceTagMappingList[].ResourceARN'
```
