# Security groups: who may talk to whom. All in the shared default VPC, all named with the team prefix.

# Every ECS task of the stack (app, engine, auth, Kafka, migrations, pipeline) carries this group.
# They talk to each other freely, like containers on one docker-compose network; nothing outside
# gets in, except what the load balancer group is given later (Step 6).
resource "aws_security_group" "services" {
  name        = "${var.prefix}-services-sg"
  description = "${var.prefix} ECS tasks, reachable only from each other and the load balancer"
  vpc_id      = local.vpc_id

  tags = {
    Name = "${var.prefix}-services-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "services_from_services" {
  security_group_id            = aws_security_group.services.id
  referenced_security_group_id = aws_security_group.services.id
  ip_protocol                  = "-1"
  description                  = "Tasks of the stack talk to each other"
}

# Outbound: ECR, Secrets Manager and logs (shared VPC endpoints), Alpaca and Yahoo (public subnets), RDS
resource "aws_vpc_security_group_egress_rule" "services_out" {
  security_group_id = aws_security_group.services.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
  description       = "Any outbound"
}

# The database accepts Postgres only from the stack's tasks: no IP range, no public access
resource "aws_security_group" "database" {
  name = "${var.prefix}-rds-sg"
  # AWS allows only a-z A-Z 0-9 and . _-:/()#,@[]+=&;{}!$* in security group descriptions: no apostrophes
  description = "${var.prefix} RDS, 5432 from the stack tasks only"
  vpc_id      = local.vpc_id

  tags = {
    Name = "${var.prefix}-rds-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "database_from_services" {
  security_group_id            = aws_security_group.database.id
  referenced_security_group_id = aws_security_group.services.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "Postgres from the stack tasks"
}
