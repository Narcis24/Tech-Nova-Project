# The ECS cluster, its IAM roles and log groups: shared by the one-off jobs (jobs.tf) and the services (Step 5).

resource "aws_ecs_cluster" "main" {
  name = "${var.prefix}-cluster"

  # CPU, memory and task counts per service in CloudWatch (small extra cost, used in Step 8)
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

locals {
  registry = "${local.account_id}.dkr.ecr.${var.region}.amazonaws.com"
  image    = { for name in local.images : name => "${local.registry}/${var.prefix}/${name}:${var.image_tag}" }
}

# What ECS itself uses to start a task: pull from ECR, write logs, and read the secrets it injects
resource "aws_iam_role" "execution" {
  name = "${var.prefix}-ecs-execution-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Exactly the two secrets the stack uses, nothing else in the shared account
resource "aws_iam_role_policy" "execution_secrets" {
  name = "${var.prefix}-read-secrets"
  role = aws_iam_role.execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "secretsmanager:GetSecretValue"
      Resource = [local.db_secret_arn, aws_secretsmanager_secret.app.arn]
    }]
  })
}

# One log group per container, kept a week
resource "aws_cloudwatch_log_group" "task" {
  for_each          = toset(["app", "execution-engine", "auth-service", "kafka", "migrations", "pipeline"])
  name              = "/ecs/${var.prefix}/${each.key}"
  retention_in_days = 7
}

locals {
  # Database connection for every container that needs one: host from RDS, user and password from its secret
  db_environment = [
    { name = "DB_HOST", value = aws_db_instance.main.address },
    { name = "DB_PORT", value = tostring(aws_db_instance.main.port) },
    { name = "DB_NAME", value = aws_db_instance.main.db_name },
  ]
  db_secrets = [
    { name = "DB_USER", valueFrom = "${local.db_secret_arn}:username::" },
    { name = "DB_PASSWORD", valueFrom = "${local.db_secret_arn}:password::" },
  ]
  # The same, as the libpq variables psql and psycopg read
  pg_environment = [
    { name = "PGHOST", value = aws_db_instance.main.address },
    { name = "PGPORT", value = tostring(aws_db_instance.main.port) },
    { name = "PGDATABASE", value = aws_db_instance.main.db_name },
    # RDS for Postgres 16 refuses unencrypted connections
    { name = "PGSSLMODE", value = "require" },
  ]
  pg_secrets = [
    { name = "PGUSER", valueFrom = "${local.db_secret_arn}:username::" },
    { name = "PGPASSWORD", valueFrom = "${local.db_secret_arn}:password::" },
  ]

  log_options = {
    for name, group in aws_cloudwatch_log_group.task : name => {
      logDriver = "awslogs"
      options = {
        awslogs-group         = group.name
        awslogs-region        = var.region
        awslogs-stream-prefix = name
      }
    }
  }
}
