# Looks up the shared network; creates nothing. The instructor owns the VPC, its subnets and the VPC endpoints.

data "aws_caller_identity" "current" {}

data "aws_vpc" "default" {
  default = true
}

data "aws_subnet" "public" {
  for_each = toset(var.public_subnet_ids)
  id       = each.value
}

data "aws_subnet" "private" {
  for_each = toset(var.private_subnet_ids)
  id       = each.value
}

locals {
  account_id = data.aws_caller_identity.current.account_id
  vpc_id     = data.aws_vpc.default.id
}

# Catches a mistyped subnet ID or a pair in one availability zone before anything is built on them
check "subnets" {
  assert {
    condition = alltrue([
      for s in concat(values(data.aws_subnet.public), values(data.aws_subnet.private)) : s.vpc_id == data.aws_vpc.default.id
    ])
    error_message = "Every subnet must be in the account's default VPC."
  }

  assert {
    condition     = length(distinct([for s in data.aws_subnet.public : s.availability_zone])) == 2
    error_message = "The two public subnets must be in different availability zones."
  }

  assert {
    condition     = length(distinct([for s in data.aws_subnet.private : s.availability_zone])) == 2
    error_message = "The two private subnets must be in different availability zones."
  }
}
