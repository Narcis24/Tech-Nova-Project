# Postgres on RDS, in the private subnets. AWS generates the master password and keeps it in
# Secrets Manager (manage_master_user_password): it never appears in Terraform, its state or a terminal.

resource "aws_db_subnet_group" "main" {
  name        = "${var.prefix}-db-subnets"
  description = "${var.prefix} private subnets for the database"
  subnet_ids  = var.private_subnet_ids
}

resource "aws_db_instance" "main" {
  identifier = "${var.prefix}-db"

  engine = "postgres"
  # Same major version as docker-compose; RDS keeps it on the latest 16.x
  engine_version             = "16"
  auto_minor_version_upgrade = true
  instance_class             = var.db_instance_class
  allocated_storage          = 20
  storage_type               = "gp3"
  storage_encrypted          = true

  db_name                     = "technova"
  username                    = "technova_admin"
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.database.id]
  publicly_accessible    = false
  multi_az               = false

  # A capstone database, recreated from migrations and the data pipeline: no backups to pay for,
  # and tofu destroy may delete it without a final snapshot
  backup_retention_period  = 0
  skip_final_snapshot      = true
  deletion_protection      = false
  delete_automated_backups = true

  # Version upgrades and settings changes apply straight away rather than in the next maintenance window
  apply_immediately = true
}

locals {
  # The RDS-managed secret holds JSON: {"username": ..., "password": ...}
  db_secret_arn = aws_db_instance.main.master_user_secret[0].secret_arn
}
