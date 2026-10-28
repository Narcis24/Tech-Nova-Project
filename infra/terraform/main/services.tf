# The four long-running services, the same containers as docker-compose.yml. They find each other by
# DNS in a private namespace (kafka.<prefix>.internal, ...), the way compose services use their names.

resource "aws_service_discovery_private_dns_namespace" "main" {
  name        = "${var.prefix}.internal"
  description = "${var.prefix} service names"
  vpc         = local.vpc_id
}

locals {
  dns        = aws_service_discovery_private_dns_namespace.main.name
  kafka_host = "kafka.${local.dns}:29092"
  app_secret = aws_secretsmanager_secret.app.arn

  db_env = {
    DB_HOST = aws_db_instance.main.address
    DB_PORT = tostring(aws_db_instance.main.port)
    DB_NAME = aws_db_instance.main.db_name
  }
  db_secret_refs = {
    DB_USER     = "${local.db_secret_arn}:username::"
    DB_PASSWORD = "${local.db_secret_arn}:password::"
  }

  service_defaults = {
    prefix             = var.prefix
    region             = var.region
    cluster_id         = aws_ecs_cluster.main.id
    execution_role_arn = aws_iam_role.execution.arn
    security_group_ids = [aws_security_group.services.id]
    namespace_id       = aws_service_discovery_private_dns_namespace.main.id
  }
}

# Single broker in KRaft mode (no ZooKeeper), as in docker-compose. Its data lives in the task, so a
# restart loses queued messages and topics; the app recreates the topics when it starts.
module "kafka" {
  source = "../modules/ecs-service"

  name   = "kafka"
  image  = local.image["kafka"]
  cpu    = 512
  memory = 1024
  port   = 29092

  environment = {
    KAFKA_NODE_ID                                  = "1"
    KAFKA_PROCESS_ROLES                            = "broker,controller"
    KAFKA_CONTROLLER_QUORUM_VOTERS                 = "1@localhost:29093"
    KAFKA_CONTROLLER_LISTENER_NAMES                = "CONTROLLER"
    KAFKA_LISTENERS                                = "PLAINTEXT://0.0.0.0:29092,CONTROLLER://0.0.0.0:29093"
    KAFKA_ADVERTISED_LISTENERS                     = "PLAINTEXT://${local.kafka_host}"
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP           = "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT"
    KAFKA_INTER_BROKER_LISTENER_NAME               = "PLAINTEXT"
    KAFKA_AUTO_CREATE_TOPICS_ENABLE                = "false"
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR         = "1"
    KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR = "1"
    KAFKA_TRANSACTION_STATE_LOG_MIN_ISR            = "1"
    KAFKA_HEAP_OPTS                                = "-Xmx512m -Xms512m"
  }

  # A port check, not kafka-broker-api-versions.sh: that starts a JVM (slower than the timeout on 0.5 vCPU)
  # and follows the advertised DNS name, which only exists once the task is healthy
  health_check_command      = ["CMD-SHELL", "nc -z localhost 29092 || exit 1"]
  health_check_start_period = 60

  subnet_ids      = var.private_subnet_ids
  log_group       = aws_cloudwatch_log_group.task["kafka"].name
  single_instance = true
  # The app creates its topics at start-up, so Kafka has to be up before the others are created
  wait_for_steady_state = true

  prefix             = local.service_defaults.prefix
  region             = local.service_defaults.region
  cluster_id         = local.service_defaults.cluster_id
  execution_role_arn = local.service_defaults.execution_role_arn
  security_group_ids = local.service_defaults.security_group_ids
  namespace_id       = local.service_defaults.namespace_id
}

# Fills orders against live Alpaca quotes. One instance only: waiting orders live in its memory.
# Public subnet with a public IP, only so it can reach Alpaca; its security group lets nothing in from outside.
module "execution_engine" {
  source = "../modules/ecs-service"

  name   = "execution-engine"
  image  = local.image["execution-engine"]
  cpu    = 512
  memory = 1024
  port   = 8082

  environment = {
    SPRING_KAFKA_BOOTSTRAP_SERVERS = local.kafka_host
    # Decision journal: on the task's own disk here, so it starts empty after each restart
    EXECUTION_ENGINE_JOURNAL_DIR = "/data/journal"
  }
  secrets = {
    ALPACA_KEY_ID      = "${local.app_secret}:ALPACA_KEY_ID::"
    ALPACA_SECRET_KEY  = "${local.app_secret}:ALPACA_SECRET_KEY::"
    INTERNAL_API_TOKEN = "${local.app_secret}:INTERNAL_API_TOKEN::"
  }

  health_check_command      = ["CMD-SHELL", "wget -qO- http://localhost:8082/actuator/health | grep -q '\"status\":\"UP\"' || exit 1"]
  health_check_start_period = 120

  subnet_ids       = var.public_subnet_ids
  assign_public_ip = true
  log_group        = aws_cloudwatch_log_group.task["execution-engine"].name
  single_instance  = true

  prefix             = local.service_defaults.prefix
  region             = local.service_defaults.region
  cluster_id         = local.service_defaults.cluster_id
  execution_role_arn = local.service_defaults.execution_role_arn
  security_group_ids = local.service_defaults.security_group_ids
  namespace_id       = local.service_defaults.namespace_id

  depends_on = [module.kafka]
}

# Sign-in, registration and password reset (NestJS)
module "auth_service" {
  source = "../modules/ecs-service"

  name   = "auth-service"
  image  = local.image["auth-service"]
  cpu    = 256
  memory = 512
  port   = 8082

  environment = merge(local.db_env, {
    # RDS requires encrypted connections; node-postgres reads this, without the Amazon CA bundle to verify against
    PGSSLMODE = "no-verify"
    # No mail server in AWS: password reset emails fail to send, everything else works
    SMTP_HOST = "localhost"
    SMTP_PORT = "25"
    MAIL_FROM = "TechNova <no-reply@technova.local>"
    # The page reset links open, on the CloudFront domain
    RESET_URL = "https://${aws_cloudfront_distribution.main.domain_name}/reset-password"
  })
  secrets = merge(local.db_secret_refs, {
    JWT_SECRET = "${local.app_secret}:JWT_SECRET::"
  })

  health_check_command      = ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:8082/auth/health || exit 1"]
  health_check_start_period = 30

  subnet_ids = var.private_subnet_ids
  log_group  = aws_cloudwatch_log_group.task["auth-service"].name
  load_balancer = {
    target_group_arn           = aws_lb_target_group.service["auth-service"].arn
    health_check_grace_seconds = 60
  }

  prefix             = local.service_defaults.prefix
  region             = local.service_defaults.region
  cluster_id         = local.service_defaults.cluster_id
  execution_role_arn = local.service_defaults.execution_role_arn
  security_group_ids = local.service_defaults.security_group_ids
  namespace_id       = local.service_defaults.namespace_id
}

# The trading API (Spring Boot), behind the load balancer at /api/*
module "app" {
  source = "../modules/ecs-service"

  name   = "app"
  image  = local.image["app"]
  cpu    = 512
  memory = 1024
  port   = 8081

  environment = merge(local.db_env, {
    AUTH_URL                       = "http://auth-service.${local.dns}:8082"
    ENGINE_URL                     = "http://execution-engine.${local.dns}:8082"
    SPRING_KAFKA_BOOTSTRAP_SERVERS = local.kafka_host
    JAVA_TOOL_OPTIONS              = "-XX:MaxRAMPercentage=75.0"
  })
  secrets = merge(local.db_secret_refs, {
    JWT_SECRET         = "${local.app_secret}:JWT_SECRET::"
    INTERNAL_API_TOKEN = "${local.app_secret}:INTERNAL_API_TOKEN::"
  })

  subnet_ids = var.private_subnet_ids
  log_group  = aws_cloudwatch_log_group.task["app"].name
  load_balancer = {
    target_group_arn           = aws_lb_target_group.service["app"].arn
    health_check_grace_seconds = 180
  }

  prefix             = local.service_defaults.prefix
  region             = local.service_defaults.region
  cluster_id         = local.service_defaults.cluster_id
  execution_role_arn = local.service_defaults.execution_role_arn
  security_group_ids = local.service_defaults.security_group_ids
  namespace_id       = local.service_defaults.namespace_id

  depends_on = [module.kafka]
}
