output "account_id" {
  value = local.account_id
}

output "vpc_id" {
  value = local.vpc_id
}

output "public_subnets" {
  description = "Public subnet ID and availability zone"
  value       = { for id, s in data.aws_subnet.public : id => s.availability_zone }
}

output "private_subnets" {
  description = "Private subnet ID and availability zone"
  value       = { for id, s in data.aws_subnet.private : id => s.availability_zone }
}

output "ecr_registry" {
  description = "Where build-and-push.sh pushes to"
  value       = "${local.account_id}.dkr.ecr.${var.region}.amazonaws.com"
}

output "ecr_repositories" {
  value = { for k, r in aws_ecr_repository.image : k => r.repository_url }
}

output "database_endpoint" {
  description = "Private: reachable only from the stack's ECS tasks"
  value       = aws_db_instance.main.address
}

output "database_secret_arn" {
  description = "AWS-managed master credentials; the password itself is never shown"
  value       = local.db_secret_arn
}

output "app_secret_arn" {
  value = aws_secretsmanager_secret.app.arn
}

output "site_url" {
  description = "Open this: the UI, with /api and /auth on the same domain"
  value       = "https://${aws_cloudfront_distribution.main.domain_name}"
}

output "dashboard_url" {
  value = "https://${var.region}.console.aws.amazon.com/cloudwatch/home?region=${var.region}#dashboards/dashboard/${aws_cloudwatch_dashboard.main.dashboard_name}"
}

output "ui_deploy" {
  description = "What infra/scripts/deploy-ui.sh needs"
  value = {
    bucket          = aws_s3_bucket.ui.bucket
    distribution_id = aws_cloudfront_distribution.main.id
  }
}

# What infra/scripts/run-task.sh needs to start a one-off job
output "run_task" {
  value = {
    cluster          = aws_ecs_cluster.main.name
    security_group   = aws_security_group.services.id
    private_subnet   = var.private_subnet_ids[0]
    public_subnet    = var.public_subnet_ids[0]
    task_definitions = { for name, t in aws_ecs_task_definition.job : name => t.family }
    log_groups       = { for name, job in local.jobs : name => aws_cloudwatch_log_group.task[job.log].name }
  }
}
