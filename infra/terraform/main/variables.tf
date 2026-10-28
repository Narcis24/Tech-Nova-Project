variable "prefix" {
  description = "Team prefix on every resource name: lowercase, letters, numbers and hyphens, 16 characters at most"
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9-]{1,16}$", var.prefix))
    error_message = "Use lowercase letters, numbers and hyphens, 16 characters at most (S3 and load balancer names need it)."
  }
}

variable "region" {
  type    = string
  default = "eu-west-1"
}

# The shared class account's network belongs to the instructor: we only look it up, never create or change it.
# The subnets carry no name tags, so their IDs come from terraform.tfvars, like PUB_A / PRIV_A in the Sprint 11 guide.
variable "public_subnet_ids" {
  description = "Two public subnets in different availability zones (route to the internet gateway): load balancer, and the tasks that call the internet"
  type        = list(string)

  validation {
    condition     = length(var.public_subnet_ids) == 2
    error_message = "Give exactly two public subnets, in different availability zones."
  }
}

variable "image_tag" {
  description = "The image tag every task runs: the git commit build-and-push.sh printed"
  type        = string
}

variable "alert_email" {
  description = "Where alarms are emailed; empty for none. AWS sends a confirmation link to click first"
  type        = string
  default     = ""
}

variable "db_instance_class" {
  description = "RDS instance size; the smallest is plenty for the capstone"
  type        = string
  default     = "db.t4g.micro"
}

# Market data keys for the execution engine. Never in terraform.tfvars: pass them from the
# environment, e.g.  export TF_VAR_alpaca_key_id=... TF_VAR_alpaca_secret_key=...
# Left empty, the engine runs without live quotes.
variable "alpaca_key_id" {
  type      = string
  default   = ""
  sensitive = true
}

variable "alpaca_secret_key" {
  type      = string
  default   = ""
  sensitive = true
}

variable "private_subnet_ids" {
  description = "Two private subnets in different availability zones (no internet route; the shared VPC endpoints reach ECR, logs and Secrets Manager)"
  type        = list(string)

  validation {
    condition     = length(var.private_subnet_ids) == 2
    error_message = "Give exactly two private subnets, in different availability zones."
  }
}
