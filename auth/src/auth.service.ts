import {
  BadRequestException,
  ConflictException,
  Injectable,
  Logger,
  UnauthorizedException,
} from '@nestjs/common';
import bcrypt from 'bcryptjs';
import { createHash, randomBytes } from 'node:crypto';
import { AuthRepository, TokenOwner } from './auth.repository.js';
import type {
  Credentials,
  RegisterRequest,
  RegisterResponse,
  TokenResponse,
} from './dto.js';
import { ACCESS_TOKEN_SECONDS, JwtService } from './jwt.service.js';
import { MailService } from './mail.service.js';

const BCRYPT_ROUNDS = 12;
const REFRESH_TOKEN_SECONDS = 7 * 24 * 60 * 60;
const RESET_TOKEN_SECONDS = 30 * 60;

// Compared against when the username is unknown, so both login failures take as long
const DUMMY_HASH = bcrypt.hashSync('not-a-real-password', BCRYPT_ROUNDS);

/** Refresh and password reset tokens are stored only as this hash. */
export function hashToken(token: string): string {
  return createHash('sha256').update(token).digest('hex');
}

@Injectable()
export class AuthService {
  private readonly logger = new Logger(AuthService.name);
  // The UI page the reset email links to. The token goes after # so browsers never send it to any server.
  private readonly resetUrl =
    process.env.RESET_URL ?? 'http://localhost:4200/reset-password';

  constructor(
    private readonly repository: AuthRepository,
    private readonly jwtService: JwtService,
    private readonly mailService: MailService,
  ) {}

  async register({
    username,
    password,
    email,
  }: RegisterRequest): Promise<RegisterResponse> {
    const passwordHash = await bcrypt.hash(password, BCRYPT_ROUNDS);
    if (
      !(await this.repository.createUser({ username, passwordHash, email }))
    ) {
      // Does not say which one, to give away as little as possible
      throw new ConflictException('Username or email already registered');
    }
    return { username };
  }

  async login({ username, password }: Credentials): Promise<TokenResponse> {
    const user = await this.repository.findUser(username);

    // A malformed stored hash counts as a mismatch
    const matches = await bcrypt
      .compare(password, user?.passwordHash ?? DUMMY_HASH)
      .catch(() => false);
    // No role means no access; same message, so the response does not reveal the account exists
    if (user === null || !matches || user.roles.length === 0) {
      throw new UnauthorizedException('Invalid username or password');
    }

    const refreshToken = randomBytes(32).toString('base64url');
    await this.repository.saveRefreshToken(
      hashToken(refreshToken),
      user.username,
      REFRESH_TOKEN_SECONDS,
    );
    return this.tokenResponse(user, refreshToken);
  }

  /** The new access token carries the user's roles as they are now, so a role change shows up here. */
  async refresh(refreshToken: string): Promise<TokenResponse> {
    const next = randomBytes(32).toString('base64url');
    const owner = await this.repository.rotateRefreshToken(
      hashToken(refreshToken),
      hashToken(next),
      REFRESH_TOKEN_SECONDS,
    );
    if (owner === null || owner.roles.length === 0) {
      throw new UnauthorizedException('Invalid or expired refresh token');
    }
    return this.tokenResponse(owner, next);
  }

  async logout(refreshToken: string): Promise<void> {
    await this.repository.revokeRefreshToken(hashToken(refreshToken));
  }

  /**
   * Emails a reset link if an account uses the email; does nothing otherwise. Never throws, so the
   * caller can answer the same way in both cases without waiting for it.
   */
  async forgotPassword(email: string): Promise<void> {
    try {
      const user = await this.repository.findUserByEmail(email);
      if (user === null) {
        return;
      }
      const token = randomBytes(32).toString('base64url');
      await this.repository.saveResetToken(
        hashToken(token),
        user.username,
        RESET_TOKEN_SECONDS,
      );
      await this.mailService.sendPasswordReset(
        email,
        `${this.resetUrl}#token=${token}`,
      );
    } catch (err) {
      // No email address in the log
      this.logger.error(
        `Password reset email failed: ${(err as Error).message}`,
      );
    }
  }

  /** Sets the new password and signs the user out everywhere. */
  async resetPassword(token: string, newPassword: string): Promise<void> {
    const passwordHash = await bcrypt.hash(newPassword, BCRYPT_ROUNDS);
    if (
      !(await this.repository.resetPassword(hashToken(token), passwordHash))
    ) {
      throw new BadRequestException('Invalid or expired reset link');
    }
  }

  private tokenResponse(
    { username, roles }: TokenOwner,
    refreshToken: string,
  ): TokenResponse {
    return {
      accessToken: this.jwtService.generateAccessToken(username, roles),
      refreshToken,
      tokenType: 'Bearer',
      expiresIn: ACCESS_TOKEN_SECONDS,
    };
  }
}
