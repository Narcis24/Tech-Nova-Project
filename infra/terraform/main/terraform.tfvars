# The only file that changes per team or account. No secrets here: those go to Secrets Manager.
prefix = "tech-nova-team"
region = "eu-west-1"

# From infra/scripts/build-and-push.sh; change it to deploy a new build
image_tag = "d7324ac"

# Shared class account (081452990196), default VPC vpc-07c5d2a3f09dffc30
public_subnet_ids  = ["subnet-048ea092ac9ad358b", "subnet-05c0d4425955ba550"] # eu-west-1a, eu-west-1b
private_subnet_ids = ["subnet-084c7b53a1d0554d0", "subnet-05ab71181c3288f8f"] # eu-west-1a, eu-west-1b
