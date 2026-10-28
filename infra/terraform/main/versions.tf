terraform {
  # 1.10+ locks S3 state with a lock file, no DynamoDB table needed
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    # Generates the JWT secret and the internal API token
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Bucket and key come from backend.hcl: tofu init -backend-config=backend.hcl
  backend "s3" {}
}

provider "aws" {
  region = var.region

  # Every resource carries the team's tags, so classmates and the instructor can see whose it is
  default_tags {
    tags = {
      Team      = var.prefix
      Project   = "tech-nova"
      ManagedBy = "terraform"
    }
  }
}
