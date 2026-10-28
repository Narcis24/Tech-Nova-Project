# The API behind CloudFront: one Application Load Balancer in the public subnets. It sends /api/* to
# the app and /auth/* to auth-service, unchanged, the same split the UI's nginx makes locally. It only
# accepts CloudFront's servers, and only requests carrying CloudFront's secret header (cdn.tf).

resource "aws_security_group" "alb" {
  name        = "${var.prefix}-alb-sg"
  description = "${var.prefix} public load balancer"
  vpc_id      = local.vpc_id

  tags = {
    Name = "${var.prefix}-alb-sg"
  }
}

# Only CloudFront's servers can connect; AWS keeps this list of their addresses up to date
data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  security_group_id = aws_security_group.alb.id
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront.id
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  description       = "HTTP from CloudFront only"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_services" {
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = aws_security_group.services.id
  ip_protocol                  = "tcp"
  from_port                    = 8081
  to_port                      = 8082
  description                  = "To the app and auth-service tasks"
}

# Only the load balancer reaches the tasks from outside the stack, and only on the two service ports
resource "aws_vpc_security_group_ingress_rule" "services_from_alb" {
  security_group_id            = aws_security_group.services.id
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = 8081
  to_port                      = 8082
  description                  = "App and auth-service from the load balancer"
}

resource "aws_lb" "main" {
  name               = "${var.prefix}-alb"
  load_balancer_type = "application"
  subnets            = var.public_subnet_ids
  security_groups    = [aws_security_group.alb.id]
}

locals {
  # Spring Boot takes about a minute to start on Fargate: check often, trust two passes, and let
  # ECS ignore failed checks for the first three minutes (the guide's endless replace loop otherwise)
  target_groups = {
    app = {
      port    = 8081
      path    = "/api/actuator/health"
      matcher = "200,401" # Spring Security answers 401 without a token: any answer means it is up
      routes  = ["/api/*"]
    }
    auth-service = {
      port    = 8082
      path    = "/auth/health"
      matcher = "200"
      routes  = ["/auth/*"]
    }
  }
}

resource "aws_lb_target_group" "service" {
  for_each = local.target_groups

  name        = "${var.prefix}-${each.key == "auth-service" ? "auth" : each.key}-tg"
  port        = each.value.port
  protocol    = "HTTP"
  vpc_id      = local.vpc_id
  target_type = "ip"

  health_check {
    path                = each.value.path
    matcher             = each.value.matcher
    interval            = 10
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  # Old tasks drain for 30 seconds on a deploy instead of the default 5 minutes
  deregistration_delay = 30
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  # Anything that is neither /api nor /auth
  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "application/json"
      message_body = jsonencode({ message = "Not found" })
      status_code  = "404"
    }
  }
}

resource "aws_lb_listener_rule" "route" {
  for_each = local.target_groups

  listener_arn = aws_lb_listener.http.arn
  priority     = each.key == "app" ? 10 : 20

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.service[each.key].arn
  }

  condition {
    path_pattern {
      values = each.value.routes
    }
  }

  # Only requests CloudFront forwarded: other CloudFront distributions share its addresses, not this header
  condition {
    http_header {
      http_header_name = local.origin_secret_header
      values           = [random_password.origin_secret.result]
    }
  }
}
