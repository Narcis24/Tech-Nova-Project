import { ValidationPipe } from '@nestjs/common';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';

/** App-wide settings, shared by main.ts and the e2e tests. */
export function configure(app: NestExpressApplication) {
  app.setGlobalPrefix('auth');
  // Unknown fields are rejected, as the contract's schemas set additionalProperties: false
  app.useGlobalPipes(
    new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true }),
  );

  // Swagger UI at /auth/docs, OpenAPI document at /auth/docs-json; paths are relative to /auth
  const config = new DocumentBuilder()
    .setTitle('TechNova Auth Service API')
    .setDescription('Registers users, checks credentials and issues JWTs')
    .setVersion('2.0.0')
    .addServer('/auth')
    .build();
  SwaggerModule.setup(
    'docs',
    app,
    () =>
      SwaggerModule.createDocument(app, config, { ignoreGlobalPrefix: true }),
    { useGlobalPrefix: true, jsonDocumentUrl: 'docs-json' },
  );

  // Old springdoc URLs
  const http = app.getHttpAdapter();
  http.get('/auth/swagger-ui.html', (_req, res) =>
    http.redirect(res, 302, '/auth/docs'),
  );
  http.get('/auth/v3/api-docs', (_req, res) =>
    http.redirect(res, 302, '/auth/docs-json'),
  );
}
