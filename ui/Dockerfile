# Keep the CI runtime aligned with the Angular-supported Node version.
FROM node:22-bookworm-slim AS ci
# Chromium is required by the headless browser test configuration.
RUN apt-get update && apt-get install -y --no-install-recommends chromium ca-certificates \
    && rm -rf /var/lib/apt/lists/*
ENV CHROME_BIN=/usr/bin/chromium
ENV npm_config_cache=/tmp/.npm
WORKDIR /workspace

FROM node:22-bookworm-slim AS build
WORKDIR /app
# Install from the lockfile before copying source files to improve layer caching.
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:stable-alpine AS production
# pick up Alpine security fixes released after the base image, e.g. libexpat CVE-2026-77214, libx11 CVE-2026-88806
RUN apk upgrade --no-cache
# The official Nginx image expands this template using the runtime variables below.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
# Keep Node, npm, and source files out of the production image.
COPY --from=build /app/dist/ui/browser /usr/share/nginx/html
ENV API_UPSTREAM=app:8081
ENV AUTH_UPSTREAM=auth-service:8082
EXPOSE 8080
# Check the internal Nginx port used by the container, not the host-mapped port.
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
