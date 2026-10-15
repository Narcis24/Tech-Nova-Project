import { Module } from '@nestjs/common';
import { AuthController } from './auth.controller.js';
import { AuthRepository } from './auth.repository.js';
import { AuthService } from './auth.service.js';
import { HealthController } from './health.controller.js';
import { JwtService } from './jwt.service.js';
import { MailService } from './mail.service.js';

@Module({
  controllers: [AuthController, HealthController],
  providers: [
    AuthService,
    AuthRepository,
    { provide: JwtService, useFactory: () => new JwtService() },
    { provide: MailService, useFactory: () => new MailService() },
  ],
})
export class AppModule {}
