terraform {
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.0" }
  }
  backend "s3" {
    bucket = "securebank-terraform-state"
    key    = "prod/terraform.tfstate"
    region = "us-east-1"
  }
}

provider "aws" { region = var.aws_region }

variable "aws_region" { default = "us-east-1" }
variable "environment" { default = "production" }

# VPC
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  tags = { Name = "securebank-vpc" }
}

resource "aws_subnet" "private" {
  count             = 2
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.${count.index + 1}.0/24"
  availability_zone = data.aws_availability_zones.available.names[count.index]
  tags = { Name = "securebank-private-${count.index + 1}" }
}

resource "aws_subnet" "public" {
  count                   = 2
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.${count.index + 10}.0/24"
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true
  tags = { Name = "securebank-public-${count.index + 1}" }
}

data "aws_availability_zones" "available" { state = "available" }

# ECS Cluster
resource "aws_ecs_cluster" "main" {
  name = "securebank-cluster"
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

# ECS Task Definitions
resource "aws_ecs_task_definition" "account_service" {
  family                   = "account-service"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name  = "account-service"
    image = "${aws_ecr_repository.services["account"].repository_url}:latest"
    portMappings = [{ containerPort = 8081, protocol = "tcp" }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "production" },
      { name = "DB_HOST", value = aws_db_instance.main.address }
    ]
    secrets = [
      { name = "DB_PASSWORD", valueFrom = aws_secretsmanager_secret.db_password.arn }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = "/ecs/account-service"
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "ecs"
      }
    }
    healthCheck = {
      command     = ["CMD-SHELL", "curl -f http://localhost:8081/actuator/health || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
    }
  }])
}

resource "aws_ecs_task_definition" "transaction_service" {
  family                   = "transaction-service"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 1024
  memory                   = 2048
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name  = "transaction-service"
    image = "${aws_ecr_repository.services["transaction"].repository_url}:latest"
    portMappings = [{ containerPort = 8082, protocol = "tcp" }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "production" },
      { name = "KAFKA_SERVERS", value = aws_msk_cluster.main.bootstrap_brokers }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = "/ecs/transaction-service"
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "ecs"
      }
    }
  }])
}

# ECR Repositories
resource "aws_ecr_repository" "services" {
  for_each = toset(["account", "transaction", "fraud", "notification", "gateway", "frontend"])
  name     = "securebank-${each.key}"
  image_scanning_configuration { scan_on_push = true }
}

# RDS PostgreSQL
resource "aws_db_instance" "main" {
  identifier             = "securebank-db"
  engine                 = "postgres"
  engine_version         = "15.4"
  instance_class         = "db.r6g.large"
  allocated_storage      = 100
  storage_encrypted      = true
  db_name                = "securebank"
  username               = "postgres"
  password               = random_password.db_password.result
  multi_az               = true
  backup_retention_period = 7
  skip_final_snapshot    = false
  vpc_security_group_ids = [aws_security_group.rds.id]
  db_subnet_group_name   = aws_db_subnet_group.main.name
}

resource "random_password" "db_password" {
  length  = 32
  special = true
}

resource "aws_secretsmanager_secret" "db_password" {
  name = "securebank/db-password"
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db_password.result
}

# MSK (Managed Kafka)
resource "aws_msk_cluster" "main" {
  cluster_name           = "securebank-kafka"
  kafka_version          = "3.5.1"
  number_of_broker_nodes = 3

  broker_node_group_info {
    instance_type   = "kafka.m5.large"
    client_subnets  = aws_subnet.private[*].id
    security_groups = [aws_security_group.kafka.id]
    storage_info {
      ebs_storage_info { volume_size = 100 }
    }
  }

  encryption_info {
    encryption_in_transit { client_broker = "TLS" }
  }
}

# ALB
resource "aws_lb" "main" {
  name               = "securebank-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id
}

# Security Groups
resource "aws_security_group" "alb" {
  name   = "securebank-alb"
  vpc_id = aws_vpc.main.id
  ingress { from_port = 443; to_port = 443; protocol = "tcp"; cidr_blocks = ["0.0.0.0/0"] }
  egress { from_port = 0; to_port = 0; protocol = "-1"; cidr_blocks = ["0.0.0.0/0"] }
}

resource "aws_security_group" "rds" {
  name   = "securebank-rds"
  vpc_id = aws_vpc.main.id
  ingress { from_port = 5432; to_port = 5432; protocol = "tcp"; security_groups = [aws_security_group.ecs.id] }
}

resource "aws_security_group" "ecs" {
  name   = "securebank-ecs"
  vpc_id = aws_vpc.main.id
  ingress { from_port = 8080; to_port = 8089; protocol = "tcp"; security_groups = [aws_security_group.alb.id] }
  egress { from_port = 0; to_port = 0; protocol = "-1"; cidr_blocks = ["0.0.0.0/0"] }
}

resource "aws_security_group" "kafka" {
  name   = "securebank-kafka"
  vpc_id = aws_vpc.main.id
  ingress { from_port = 9092; to_port = 9094; protocol = "tcp"; security_groups = [aws_security_group.ecs.id] }
}

resource "aws_db_subnet_group" "main" {
  name       = "securebank-db-subnet"
  subnet_ids = aws_subnet.private[*].id
}

# IAM Roles
resource "aws_iam_role" "ecs_execution" {
  name = "securebank-ecs-execution"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{ Action = "sts:AssumeRole", Effect = "Allow", Principal = { Service = "ecs-tasks.amazonaws.com" } }]
  })
}

resource "aws_iam_role" "ecs_task" {
  name = "securebank-ecs-task"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{ Action = "sts:AssumeRole", Effect = "Allow", Principal = { Service = "ecs-tasks.amazonaws.com" } }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Outputs
output "alb_dns" { value = aws_lb.main.dns_name }
output "rds_endpoint" { value = aws_db_instance.main.endpoint }
output "kafka_brokers" { value = aws_msk_cluster.main.bootstrap_brokers }
