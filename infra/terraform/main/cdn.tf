# The front door: one CloudFront distribution over HTTPS. The Angular build comes from a private S3
# bucket; /api/* and /auth/* go to the load balancer, so the browser sees one domain and needs no CORS,
# exactly as with the UI container's nginx locally.

resource "aws_s3_bucket" "ui" {
  bucket = "${var.prefix}-ui-${local.account_id}"
  # Holds only build output, which deploy-ui.sh can upload again: destroy may empty it
  force_destroy = true
}

# Private, as in the guide's Module 5: only this distribution can read it
resource "aws_s3_bucket_public_access_block" "ui" {
  bucket                  = aws_s3_bucket.ui.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_cloudfront_origin_access_control" "ui" {
  name                              = "${var.prefix}-ui-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_s3_bucket_policy" "ui" {
  bucket = aws_s3_bucket.ui.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "AllowCloudFrontServicePrincipal"
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.ui.arn}/*"
      Condition = { StringEquals = { "AWS:SourceArn" = aws_cloudfront_distribution.main.arn } }
    }]
  })
  depends_on = [aws_s3_bucket_public_access_block.ui]
}

resource "aws_cloudfront_function" "spa_rewrite" {
  name    = "${var.prefix}-spa-rewrite"
  runtime = "cloudfront-js-2.0"
  comment = "Deep links get index.html"
  publish = true
  code    = file("${path.module}/spa-rewrite.js")
}

# CloudFront sends this header to the load balancer, which refuses requests without it (alb.tf),
# so the API cannot be reached around CloudFront
resource "random_password" "origin_secret" {
  length  = 32
  special = false
}

locals {
  # AWS managed policies, as in the guide's cache policy ID
  cache_optimized        = "658327ea-f89d-4fab-a63d-7e88639e58f6" # CachingOptimized
  cache_disabled         = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad" # CachingDisabled
  all_viewer_except_host = "b689b0a8-53d0-40ab-baf2-68738e2966ac" # forwards headers, cookies, query strings
  origin_secret_header   = "X-Origin-Verify"
  s3_origin_id           = "ui-bucket"
  alb_origin_id          = "api-load-balancer"
  api_paths              = ["/api/*", "/auth/*"]
}

resource "aws_cloudfront_distribution" "main" {
  enabled             = true
  comment             = "${var.prefix} Tech Nova"
  default_root_object = "index.html"
  # Europe and North America edges only: the cheapest price class
  price_class = "PriceClass_100"

  origin {
    origin_id                = local.s3_origin_id
    domain_name              = aws_s3_bucket.ui.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.ui.id
  }

  origin {
    origin_id   = local.alb_origin_id
    domain_name = aws_lb.main.dns_name

    # The load balancer has no certificate; the browser's connection to CloudFront is HTTPS
    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }

    custom_header {
      name  = local.origin_secret_header
      value = random_password.origin_secret.result
    }
  }

  # The Angular app
  default_cache_behavior {
    target_origin_id       = local.s3_origin_id
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = local.cache_optimized
    compress               = true

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_rewrite.arn
    }
  }

  # The API: never cached, every method, all headers (the JWT) passed through
  dynamic "ordered_cache_behavior" {
    for_each = local.api_paths
    content {
      path_pattern             = ordered_cache_behavior.value
      target_origin_id         = local.alb_origin_id
      viewer_protocol_policy   = "redirect-to-https"
      allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
      cached_methods           = ["GET", "HEAD"]
      cache_policy_id          = local.cache_disabled
      origin_request_policy_id = local.all_viewer_except_host
      compress                 = true
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # The free *.cloudfront.net certificate; a custom domain would need its own ACM certificate
  viewer_certificate {
    cloudfront_default_certificate = true
  }
}
