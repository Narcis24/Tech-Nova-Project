# Node 22 is supported by Angular 19. Keep build and CI runtimes aligned.
FROM node:22-bookworm-slim AS ci
RUN apt-get update && apt-get install -y --no-install-recommends chromium ca-certificates \
    && rm -rf /var/lib/apt/lists/*
ENV CHROME_BIN=/usr/bin/chromium
ENV npm_config_cache=/tmp/.npm
WORKDIR /workspace

FROM node:22-bookworm-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:stable-alpine AS production
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/ui/browser /usr/share/nginx/html
ENV API_UPSTREAM=app:8081
ENV AUTH_UPSTREAM=auth-service:8082
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
