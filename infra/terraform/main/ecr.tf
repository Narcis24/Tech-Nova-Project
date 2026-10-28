# One private image repository per container the stack runs. Images are built and pushed by
# infra/scripts/build-and-push.sh, outside Terraform; task definitions pick them by var.image_tag.
locals {
  images = toset([
    "app",              # Trading API, Spring Boot
    "execution-engine", # Order execution and market data, Spring Boot
    "auth-service",     # Sign-in and accounts, NestJS
    "kafka",            # apache/kafka, copied here because private tasks cannot reach Docker Hub
    "pipeline",         # Price and company data loader, Python
    "migrations",       # Applies db/migrations to RDS, once per file
  ])
}

resource "aws_ecr_repository" "image" {
  for_each = local.images

  name                 = "${var.prefix}/${each.key}"
  image_tag_mutability = "MUTABLE"

  # Lets tofu destroy remove a repository that still holds images
  force_delete = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

# Each build pushes a new tag: keep the last 10 and drop untagged layers after a day
resource "aws_ecr_lifecycle_policy" "image" {
  for_each   = aws_ecr_repository.image
  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after 1 day"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 1
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "Keep the 10 most recent tagged images"
        selection = {
          tagStatus      = "tagged"
          tagPatternList = ["*"]
          countType      = "imageCountMoreThan"
          countNumber    = 10
        }
        action = { type = "expire" }
      },
    ]
  })
}
