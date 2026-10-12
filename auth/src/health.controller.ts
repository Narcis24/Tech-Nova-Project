import { Controller, Get } from '@nestjs/common';
import { ApiExcludeController } from '@nestjs/swagger';

/** Used by the Docker health check; not part of the API contract. */
@ApiExcludeController()
@Controller('health')
export class HealthController {
  @Get()
  health() {
    return { status: 'ok' };
  }
}
