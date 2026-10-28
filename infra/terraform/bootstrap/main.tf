# One-off: the S3 bucket that holds the main stack's OpenTofu state.
# Applied once with local state (bootstrap/terraform.tfstate, git-ignored); everything else lives in ../main.

terraform {
  required_version = ">= 1.10"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

variable "prefix" {
  description = "Team prefix on every resource name: lowercase, letters, numbers and hyphens, 16 characters at most"
  type        = string
  default     = "tech-nova-team"
}

variable "region" {
  type    = string
  default = "eu-west-1"
}

provider "aws" {
  region = var.region
  default_tags {
    tags = {
      Team      = var.prefix
      ManagedBy = "terraform"
    }
  }
}

data "aws_caller_identity" "current" {}

# Bucket names are global across every AWS account, so the account ID keeps ours unique
resource "aws_s3_bucket" "state" {
  bucket = "${var.prefix}-tfstate-${data.aws_caller_identity.current.account_id}"

  # Losing the state means OpenTofu forgets what it built; destroying the bucket must be deliberate
  lifecycle {
    prevent_destroy = true
  }
}

# Every apply keeps the previous state as an older version, so a bad apply can be rolled back
resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# State can contain secrets (database endpoints, generated values): never public
resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

output "state_bucket" {
  value = aws_s3_bucket.state.bucket
}
