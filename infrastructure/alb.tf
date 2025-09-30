# Public Application Load Balancer
resource "aws_lb" "public" {
  name               = "${local.name}-pub-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.public_alb.id]
  subnets            = data.aws_subnets.default.ids

  enable_deletion_protection = false

  tags = local.tags
}

# Public ALB Security Group
resource "aws_security_group" "public_alb" {
  name        = "${local.name}-public-alb-sg"
  description = "Security group for Public Application Load Balancer"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.tags
}

# Public ALB Listener
resource "aws_lb_listener" "public" {
  load_balancer_arn = aws_lb.public.arn
  port              = "80"
  protocol          = "HTTP"

  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      message_body = "Semantic Search Engine"
      status_code  = "200"
    }
  }

  tags = local.tags
}

# ALB Target Groups
resource "aws_lb_target_group" "search_api" {
  name     = "${local.name}-search-tg"
  port     = 8080
  protocol = "HTTP"
  vpc_id   = data.aws_vpc.default.id
  target_type = "ip"

  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 2
    timeout             = 5
    interval            = 30
    path                = "/health"
    matcher             = "200"
    port                = "traffic-port"
    protocol            = "HTTP"
  }

  tags = local.tags
}

resource "aws_lb_target_group" "ml_model" {
  name     = "${local.name}-ml-tg"
  port     = 8000
  protocol = "HTTP"
  vpc_id   = data.aws_vpc.default.id
  target_type = "ip"

  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 2
    timeout             = 5
    interval            = 30
    path                = "/health"
    matcher             = "200"
    port                = "traffic-port"
    protocol            = "HTTP"
  }

  tags = local.tags
}

# ALB Listener Rules
resource "aws_lb_listener_rule" "search_api" {
  listener_arn = aws_lb_listener.public.arn
  priority     = 100

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.search_api.arn
  }

  condition {
    path_pattern {
      values = ["/api/*"]
    }
  }

  tags = local.tags
}

# Health check rule for Search API
resource "aws_lb_listener_rule" "search_api_health" {
  listener_arn = aws_lb_listener.public.arn
  priority     = 150

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.search_api.arn
  }

  condition {
    path_pattern {
      values = ["/api/v1/health"]
    }
  }

  tags = local.tags
}

resource "aws_lb_listener_rule" "ml_model" {
  listener_arn = aws_lb_listener.public.arn
  priority     = 200

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.ml_model.arn
  }

  condition {
    path_pattern {
      values = ["/embed*", "/docs*"]
    }
  }

  tags = local.tags
}

# Health check rule for ML Model
resource "aws_lb_listener_rule" "ml_model_health" {
  listener_arn = aws_lb_listener.public.arn
  priority     = 250

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.ml_model.arn
  }

  condition {
    path_pattern {
      values = ["/health"]
    }
  }

  tags = local.tags
}