import { Controller, Get, UseGuards } from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiForbiddenResponse,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
  ApiUnauthorizedResponse,
} from '@nestjs/swagger';
import { AdminGuard } from './admin.guard.js';
import { AdminService } from './admin.service.js';
import { AdminUser, ErrorResponse } from './dto.js';

/** User management. The guard on the class covers every route, including ones added later. */
@ApiTags('admin')
@ApiBearerAuth()
@UseGuards(AdminGuard)
@Controller('v1/admin/users')
@ApiUnauthorizedResponse({
  description: 'Access token missing, forged, expired or malformed',
  type: ErrorResponse,
})
@ApiForbiddenResponse({
  description: 'Access token valid but without the ADMIN role',
  type: ErrorResponse,
})
export class AdminController {
  constructor(private readonly adminService: AdminService) {}

  @Get()
  @ApiOperation({
    operationId: 'listUsers',
    summary: 'List every user with their roles',
  })
  @ApiOkResponse({ description: 'Every user, by username', type: [AdminUser] })
  listUsers(): Promise<AdminUser[]> {
    return this.adminService.listUsers();
  }
}
