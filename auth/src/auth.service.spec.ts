import {
  BadRequestException,
  ConflictException,
  UnauthorizedException,
} from '@nestjs/common';
import bcrypt from 'bcryptjs';
import {
  AuthRepository,
  NewUser,
  TokenOwner,
  User,
} from './auth.repository.js';
import { AuthService, hashToken } from './auth.service.js';
import { JwtService } from './jwt.service.js';
import { MailService } from './mail.service.js';

describe('AuthService', () => {
  const repository = {
    findUser: vi.fn<(username: string) => Promise<User | null>>(),
    findUserByEmail: vi.fn<(email: string) => Promise<User | null>>(),
    createUser: vi.fn<(user: NewUser) => Promise<boolean>>(),
    saveRefreshToken:
      vi.fn<(hash: string, username: string, ttl: number) => Promise<void>>(),
    rotateRefreshToken:
      vi.fn<
        (
          oldHash: string,
          newHash: string,
          ttl: number,
        ) => Promise<TokenOwner | null>
      >(),
    revokeRefreshToken: vi.fn<(hash: string) => Promise<void>>(),
    saveResetToken:
      vi.fn<(hash: string, username: string, ttl: number) => Promise<void>>(),
    resetPassword:
      vi.fn<(tokenHash: string, passwordHash: string) => Promise<boolean>>(),
  };
  const jwtService = { generateAccessToken: vi.fn(() => 'access-token') };
  const mailService = {
    sendPasswordReset: vi.fn<(to: string, link: string) => Promise<void>>(),
  };
  const authService = new AuthService(
    repository as unknown as AuthRepository,
    jwtService as unknown as JwtService,
    mailService as unknown as MailService,
  );
  const credentials = { username: 'john_doe1', password: 'Something123@' };
  const registration = { ...credentials, email: 'john.doe@example.com' };
  const SEVEN_DAYS = 7 * 24 * 60 * 60;

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('registers with the email and a cost 12 bcrypt hash', async () => {
    repository.createUser.mockResolvedValue(true);

    await expect(authService.register(registration)).resolves.toEqual({
      username: 'john_doe1',
    });
    const saved = repository.createUser.mock.calls[0][0];
    expect(saved.email).toBe('john.doe@example.com');
    expect(saved.passwordHash).toMatch(/^\$2[aby]\$12\$/);
    expect(await bcrypt.compare('Something123@', saved.passwordHash)).toBe(
      true,
    );
  });

  it('rejects a taken username or email with a 409 that does not say which', async () => {
    repository.createUser.mockResolvedValue(false);

    await expect(authService.register(registration)).rejects.toThrow(
      new ConflictException('Username or email already registered'),
    );
  });

  it('logs in and stores only a hash of the refresh token', async () => {
    const passwordHash = await bcrypt.hash('Something123@', 4);
    repository.findUser.mockResolvedValue({
      username: 'john_doe1',
      passwordHash,
      roles: ['TRADER'],
    });

    const response = await authService.login(credentials);

    expect(response).toEqual({
      accessToken: 'access-token',
      refreshToken: expect.any(String),
      tokenType: 'Bearer',
      expiresIn: 900,
    });
    expect(jwtService.generateAccessToken).toHaveBeenCalledWith('john_doe1', [
      'TRADER',
    ]);
    expect(repository.saveRefreshToken).toHaveBeenCalledWith(
      hashToken(response.refreshToken),
      'john_doe1',
      SEVEN_DAYS,
    );
  });

  it('puts every role of the user into the access token', async () => {
    repository.findUser.mockResolvedValue({
      username: 'john_doe1',
      passwordHash: await bcrypt.hash('Something123@', 4),
      roles: ['ADMIN', 'TRADER'],
    });

    await authService.login(credentials);

    expect(jwtService.generateAccessToken).toHaveBeenCalledWith('john_doe1', [
      'ADMIN',
      'TRADER',
    ]);
  });

  it('refuses a user with no role the same way as a wrong password', async () => {
    repository.findUser.mockResolvedValue({
      username: 'john_doe1',
      passwordHash: await bcrypt.hash('Something123@', 4),
      roles: [],
    });

    await expect(authService.login(credentials)).rejects.toThrow(
      new UnauthorizedException('Invalid username or password'),
    );
    expect(repository.saveRefreshToken).not.toHaveBeenCalled();
  });

  it('refuses an account whose password was never set', async () => {
    repository.findUser.mockResolvedValue({
      username: 'john_doe1',
      passwordHash: null,
      roles: ['TRADER'],
    });

    await expect(authService.login(credentials)).rejects.toThrow(
      new UnauthorizedException('Invalid username or password'),
    );
  });

  it('gives the same 401 for an unknown user and a wrong password', async () => {
    const expected = new UnauthorizedException('Invalid username or password');

    repository.findUser.mockResolvedValue(null);
    await expect(authService.login(credentials)).rejects.toThrow(expected);

    repository.findUser.mockResolvedValue({
      username: 'john_doe1',
      passwordHash: await bcrypt.hash('another-password', 4),
      roles: ['TRADER'],
    });
    await expect(authService.login(credentials)).rejects.toThrow(expected);

    repository.findUser.mockResolvedValue({
      username: 'john_doe1',
      passwordHash: 'not-a-hash',
      roles: ['TRADER'],
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

  it('rotates a refresh token and issues an access token with the current roles', async () => {
    repository.rotateRefreshToken.mockResolvedValue({
      username: 'john_doe1',
      roles: ['ADMIN', 'TRADER'],
    });

    const response = await authService.refresh('old-token');

    expect(response.refreshToken).not.toBe('old-token');
    expect(repository.rotateRefreshToken).toHaveBeenCalledWith(
      hashToken('old-token'),
      hashToken(response.refreshToken),
      SEVEN_DAYS,
    );
    expect(jwtService.generateAccessToken).toHaveBeenCalledWith('john_doe1', [
      'ADMIN',
      'TRADER',
    ]);
  });

  it('rejects an unusable refresh token with 401', async () => {
    repository.rotateRefreshToken.mockResolvedValue(null);

    await expect(authService.refresh('old-token')).rejects.toThrow(
      new UnauthorizedException('Invalid or expired refresh token'),
    );
  });

  it('rejects a refresh for a user who no longer has a role', async () => {
    repository.rotateRefreshToken.mockResolvedValue({
      username: 'john_doe1',
      roles: [],
    });

    await expect(authService.refresh('old-token')).rejects.toThrow(
      new UnauthorizedException('Invalid or expired refresh token'),
    );
    expect(jwtService.generateAccessToken).not.toHaveBeenCalled();
  });

  it('logs out by revoking the hash', async () => {
    await authService.logout('some-token');
    expect(repository.revokeRefreshToken).toHaveBeenCalledWith(
      hashToken('some-token'),
    );
  });

  describe('forgotPassword', () => {
    it('emails a link whose token is stored only as a hash, valid for 30 minutes', async () => {
      repository.findUserByEmail.mockResolvedValue({
        username: 'john_doe1',
        passwordHash: 'x',
        roles: ['TRADER'],
      });

      await authService.forgotPassword('john.doe@example.com');

      const [to, link] = mailService.sendPasswordReset.mock.calls[0];
      expect(to).toBe('john.doe@example.com');
      const token = link.split('#token=')[1];
      expect(link).toBe(`http://localhost:4200/reset-password#token=${token}`);
      expect(repository.saveResetToken).toHaveBeenCalledWith(
        hashToken(token),
        'john_doe1',
        30 * 60,
      );
    });

    it('does nothing for an email no account uses', async () => {
      repository.findUserByEmail.mockResolvedValue(null);

      await authService.forgotPassword('nobody@example.com');

      expect(repository.saveResetToken).not.toHaveBeenCalled();
      expect(mailService.sendPasswordReset).not.toHaveBeenCalled();
    });

    it('never throws, even when the email cannot be sent', async () => {
      repository.findUserByEmail.mockResolvedValue({
        username: 'john_doe1',
        passwordHash: 'x',
        roles: ['TRADER'],
      });
      mailService.sendPasswordReset.mockRejectedValueOnce(
        new Error('connection refused'),
      );

      await expect(
        authService.forgotPassword('john.doe@example.com'),
      ).resolves.toBeUndefined();
    });
  });

  describe('resetPassword', () => {
    it('saves a cost 12 bcrypt hash of the new password for the token', async () => {
      repository.resetPassword.mockResolvedValue(true);

      await authService.resetPassword('reset-token', 'NewPassword123');

      const [tokenHash, passwordHash] = repository.resetPassword.mock.calls[0];
      expect(tokenHash).toBe(hashToken('reset-token'));
      expect(passwordHash).toMatch(/^\$2[aby]\$12\$/);
      expect(await bcrypt.compare('NewPassword123', passwordHash)).toBe(true);
    });

    it('rejects an unknown, used or expired token with 400', async () => {
      repository.resetPassword.mockResolvedValue(false);

      await expect(
        authService.resetPassword('bad-token', 'NewPassword123'),
      ).rejects.toThrow(
        new BadRequestException('Invalid or expired reset link'),
      );
    });
  });
});
