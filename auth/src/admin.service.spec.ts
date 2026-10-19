import { AdminService } from './admin.service.js';
import { AuthRepository } from './auth.repository.js';
import type { AdminUser } from './dto.js';

describe('AdminService', () => {
  const repository = { listUsers: vi.fn<() => Promise<AdminUser[]>>() };
  const adminService = new AdminService(
    repository as unknown as AuthRepository,
  );

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('lists the users the repository returns', async () => {
    const users: AdminUser[] = [
      {
        username: 'test_admin',
        email: 'test_admin@technova.local',
        roles: ['ADMIN'],
        createdAt: '2026-10-19T09:00:00.000Z',
        createdBy: null,
      },
    ];
    repository.listUsers.mockResolvedValue(users);

    await expect(adminService.listUsers()).resolves.toEqual(users);
  });
});
