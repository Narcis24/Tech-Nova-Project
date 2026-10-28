# The stack's own secrets, one Secrets Manager entry with a JSON value. ECS injects single keys
# into the containers (Step 5), like the database password; nothing sensitive goes in a task definition.

# Signs and checks JWTs: auth-service issues them, the app verifies them
resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

# Shared by the app and the execution engine for /internal/** calls (journal page, live quotes)
resource "random_password" "internal_api_token" {
  length  = 48
  special = false
}

resource "aws_secretsmanager_secret" "app" {
  name        = "${var.prefix}/app"
  description = "${var.prefix} JWT secret, internal API token and Alpaca market data keys"

  # Deleted at once on tofu destroy, so the name is free again for the next apply
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "app" {
  secret_id = aws_secretsmanager_secret.app.id
  secret_string = jsonencode({
    JWT_SECRET         = random_password.jwt_secret.result
    INTERNAL_API_TOKEN = random_password.internal_api_token.result
    ALPACA_KEY_ID      = var.alpaca_key_id
    ALPACA_SECRET_KEY  = var.alpaca_secret_key
  })
}
