import { ConflictException, UnauthorizedException } from '@nestjs/common';
import bcrypt from 'bcryptjs';
import { AuthRepository, User } from './auth.repository.js';
import { AuthService, hashRefreshToken } from './auth.service.js';
import { JwtService } from './jwt.service.js';

describe('AuthService', () => {
  const repository = {
    findUser: vi.fn<(username: string) => Promise<User | null>>(),
    createUser: vi.fn<(user: User) => Promise<boolean>>(),
    saveRefreshToken:
      vi.fn<(hash: string, username: string, ttl: number) => Promise<void>>(),
    rotateRefreshToken:
      vi.fn<
        (
          oldHash: string,
          newHash: string,
          ttl: number,
        ) => Promise<string | null>
      >(),
    revokeRefreshToken: vi.fn<(hash: string) => Promise<void>>(),
  };
  const jwtService = { generateAccessToken: vi.fn(() => 'access-token') };
  const authService = new AuthService(
    repository as unknown as AuthRepository,
    jwtService as unknown as JwtService,
  );
  const credentials = { username: 'john_doe1', password: 'Something123@' };
  const SEVEN_DAYS = 7 * 24 * 60 * 60;

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('registers with a cost 12 bcrypt hash', async () => {
    repository.createUser.mockResolvedValue(true);

    await expect(authService.register(credentials)).resolves.toEqual({
      username: 'john_doe1',
    });
    const saved = repository.createUser.mock.calls[0][0];
    expect(saved.passwordHash).toMatch(/^\$2[aby]\$12\$/);
    expect(await bcrypt.compare('Something123@', saved.passwordHash)).toBe(
      true,
    );
  });

  it('rejects a taken username with 409', async () => {
    repository.createUser.mockResolvedValue(false);

    await expect(authService.register(credentials)).rejects.toThrow(
      new ConflictException('Username already registered'),
    );
  });

  it('logs in and stores only a hash of the refresh token', async () => {
    const passwordHash = await bcrypt.hash('Something123@', 4);
    repository.findUser.mockResolvedValue({
      username: 'john_doe1',
      passwordHash,
    });

    const response = await authService.login(credentials);

    expect(response).toEqual({
      accessToken: 'access-token',
      refreshToken: expect.any(String),
      tokenType: 'Bearer',
      expiresIn: 900,
    });
    expect(jwtService.generateAccessToken).toHaveBeenCalledWith('john_doe1');
    expect(repository.saveRefreshToken).toHaveBeenCalledWith(
      hashRefreshToken(response.refreshToken),
      'john_doe1',
      SEVEN_DAYS,
    );
  });

  it('gives the same 401 for an unknown user and a wrong password', async () => {
    const expected = new UnauthorizedException('Invalid username or password');

    repository.findUser.mockResolvedValue(null);
    await expect(authService.login(credentials)).rejects.toThrow(expected);

    repository.findUser.mockResolvedValue({
      username: 'john_doe1',
      passwordHash: await bcrypt.hash('another-password', 4),
    });
    await expect(authService.login(credentials)).rejects.toThrow(expected);

    repository.findUser.mockResolvedValue({
      username: 'john_doe1',
      passwordHash: 'not-a-hash',
    });
    await expect(authService.login(credentials)).rejects.toThrow(expected);

    expect(repository.saveRefreshToken).not.toHaveBeenCalled();
  });

  it('checks a password even for an unknown user, so timing does not reveal it', async () => {
    repository.findUser.mockResolvedValue(null);
    const compare = vi.spyOn(bcrypt, 'compare');

    await expect(authService.login(credentials)).rejects.toThrow(
      UnauthorizedException,
    );
    expect(compare).toHaveBeenCalledWith(
      'Something123@',
      expect.stringMatching(/^\$2[aby]\$12\$/),
    );
    compare.mockRestore();
  });

  it('rotates a refresh token', async () => {
    repository.rotateRefreshToken.mockResolvedValue('john_doe1');

    const response = await authService.refresh('old-token');

    expect(response.refreshToken).not.toBe('old-token');
    expect(repository.rotateRefreshToken).toHaveBeenCalledWith(
      hashRefreshToken('old-token'),
      hashRefreshToken(response.refreshToken),
      SEVEN_DAYS,
    );
    expect(jwtService.generateAccessToken).toHaveBeenCalledWith('john_doe1');
  });

  it('rejects an unusable refresh token with 401', async () => {
    repository.rotateRefreshToken.mockResolvedValue(null);

    await expect(authService.refresh('old-token')).rejects.toThrow(
      new UnauthorizedException('Invalid or expired refresh token'),
    );
  });

  it('logs out by revoking the hash', async () => {
    await authService.logout('some-token');
    expect(repository.revokeRefreshToken).toHaveBeenCalledWith(
      hashRefreshToken('some-token'),
    );
  });
});
