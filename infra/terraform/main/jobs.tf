# One-off tasks, started with infra/scripts/run-task.sh rather than kept running:
#   migrations    applies db/migrations to RDS (private subnets; reaches ECR and secrets through the shared endpoints)
#   prices        loads daily prices from Yahoo Finance (public subnet: needs the internet)
#   fundamentals  loads company and fund facts from Yahoo Finance (public subnet: needs the internet)

locals {
  jobs = {
    migrations = {
      image   = local.image["migrations"]
      log     = "migrations"
      command = null
    }
    prices = {
      image   = local.image["pipeline"]
      log     = "pipeline"
      command = null # the image's default: python load.py
    }
    fundamentals = {
      image   = local.image["pipeline"]
      log     = "pipeline"
      command = ["fundamentals.py"]
    }
  }
}

resource "aws_ecs_task_definition" "job" {
  for_each = local.jobs

  family                   = "${var.prefix}-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = aws_iam_role.execution.arn

  runtime_platform {
    cpu_architecture        = "X86_64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([merge(
    {
      name      = each.key
      image     = each.value.image
      essential = true
      # The pipeline scripts connect with DB_URL; a bare option string leaves host, database, user
      # and password to libpq's PG* variables
      environment = concat(local.pg_environment, each.key == "migrations" ? [] : [{ name = "DB_URL", value = "sslmode=require" }])
      secrets     = local.pg_secrets
      logConfiguration = merge(local.log_options[each.value.log], {
        options = merge(local.log_options[each.value.log].options, { awslogs-stream-prefix = each.key })
      })
    },
    # fundamentals.py runs through python instead of the pipeline image's default load.py
    each.value.command == null ? {} : { entryPoint = ["python"], command = each.value.command },
  )])
}
