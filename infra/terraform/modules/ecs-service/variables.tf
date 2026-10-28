variable "name" {
  description = "Service name; also its DNS name in the private namespace, e.g. kafka -> kafka.<namespace>"
  type        = string
}

variable "prefix" {
  type = string
}

variable "region" {
  type = string
}

variable "cluster_id" {
  type = string
}

variable "image" {
  type = string
}

variable "cpu" {
  description = "CPU units: 256 = 0.25 vCPU. Spring Boot needs 512 to start in a sensible time"
  type        = number
}

variable "memory" {
  description = "MiB"
  type        = number
}

variable "port" {
  description = "The port the container listens on"
  type        = number
}

variable "environment" {
  description = "Plain settings, as { NAME = value }"
  type        = map(string)
  default     = {}
}

variable "secrets" {
  description = "Secret settings, as { NAME = \"<secret ARN>:<json key>::\" }; ECS reads them at start"
  type        = map(string)
  default     = {}
}

variable "health_check_command" {
  description = "Container health check, run inside the container; null for none"
  type        = list(string)
  default     = null
}

variable "health_check_start_period" {
  description = "Seconds after start before failed health checks count"
  type        = number
  default     = 60
}

variable "execution_role_arn" {
  type = string
}

variable "log_group" {
  type = string
}

variable "subnet_ids" {
  type = list(string)
}

variable "security_group_ids" {
  type = list(string)
}

variable "assign_public_ip" {
  description = "Only for tasks that call the internet from a public subnet; the security group still lets nothing in"
  type        = bool
  default     = false
}

variable "namespace_id" {
  description = "Cloud Map private DNS namespace the service registers in"
  type        = string
}

variable "single_instance" {
  description = "Never run two at once (Kafka, the execution engine): a deploy stops the old task before starting the new one"
  type        = bool
  default     = false
}

variable "desired_count" {
  type    = number
  default = 1
}

variable "wait_for_steady_state" {
  description = "Make tofu apply wait until the service is running, e.g. Kafka before the services that use it"
  type        = bool
  default     = false
}

variable "load_balancer" {
  description = "Target group to register in (Step 6), or null"
  type = object({
    target_group_arn           = string
    health_check_grace_seconds = number
  })
  default = null
}
