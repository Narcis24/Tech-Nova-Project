# One long-running container on Fargate: task definition, ECS service, and a DNS name the other
# services reach it by (<name>.<namespace>), like a docker-compose service name.

terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

resource "aws_service_discovery_service" "this" {
  name = var.name

  dns_config {
    namespace_id   = var.namespace_id
    routing_policy = "MULTIVALUE"

    # Short TTL: a replaced task's new address is picked up within seconds
    dns_records {
      type = "A"
      ttl  = 10
    }
  }

  # Lets tofu destroy remove it while tasks are still registered
  force_destroy = true
}

resource "aws_ecs_task_definition" "this" {
  family                   = "${var.prefix}-${var.name}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = tostring(var.cpu)
  memory                   = tostring(var.memory)
  execution_role_arn       = var.execution_role_arn

  runtime_platform {
    cpu_architecture        = "X86_64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([merge(
    {
      name         = var.name
      image        = var.image
      essential    = true
      portMappings = [{ containerPort = var.port, protocol = "tcp" }]
      environment  = [for k, v in var.environment : { name = k, value = v }]
      secrets      = [for k, v in var.secrets : { name = k, valueFrom = v }]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = var.log_group
          awslogs-region        = var.region
          awslogs-stream-prefix = var.name
        }
      }
    },
    var.health_check_command == null ? {} : {
      healthCheck = {
        command     = var.health_check_command
        interval    = 15
        timeout     = 5
        retries     = 4
        startPeriod = var.health_check_start_period
      }
    },
  )])
}

resource "aws_ecs_service" "this" {
  name            = "${var.prefix}-${var.name}"
  cluster         = var.cluster_id
  task_definition = aws_ecs_task_definition.this.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = var.security_group_ids
    assign_public_ip = var.assign_public_ip
  }

  service_registries {
    registry_arn = aws_service_discovery_service.this.arn
  }

  # Single-instance services stop the old task first; the rest start the new one before stopping the old
  deployment_minimum_healthy_percent = var.single_instance ? 0 : 100
  deployment_maximum_percent         = var.single_instance ? 100 : 200

  # A deploy that never gets healthy is rolled back to the last working task definition
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  dynamic "load_balancer" {
    for_each = var.load_balancer == null ? [] : [var.load_balancer]
    content {
      target_group_arn = load_balancer.value.target_group_arn
      container_name   = var.name
      container_port   = var.port
    }
  }

  health_check_grace_period_seconds = var.load_balancer == null ? null : var.load_balancer.health_check_grace_seconds

  wait_for_steady_state = var.wait_for_steady_state
  propagate_tags        = "SERVICE"
}
