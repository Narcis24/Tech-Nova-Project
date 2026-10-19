import { Injectable } from '@nestjs/common';
import { AuthRepository } from './auth.repository.js';
import type { AdminUser } from './dto.js';

/** User management for admins. Callers must already have passed the AdminGuard. */
@Injectable()
export class AdminService {
  constructor(private readonly repository: AuthRepository) {}

  listUsers(): Promise<AdminUser[]> {
    return this.repository.listUsers();
  }
}
