import {
  ConflictException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import bcrypt from 'bcryptjs';
import { createHash, randomBytes } from 'node:crypto';
import { AuthRepository } from './auth.repository.js';
import type { Credentials, RegisterResponse, TokenResponse } from './dto.js';
import { ACCESS_TOKEN_SECONDS, JwtService } from './jwt.service.js';

const BCRYPT_ROUNDS = 12;
const REFRESH_TOKEN_SECONDS = 7 * 24 * 60 * 60;

// Compared against when the username is unknown, so both login failures take as long
const DUMMY_HASH = bcrypt.hashSync('not-a-real-password', BCRYPT_ROUNDS);

/** Refresh tokens are stored only as this hash. */
export function hashRefreshToken(token: string): string {
  return createHash('sha256').update(token).digest('hex');
}

@Injectable()
export class AuthService {
  constructor(
    private readonly repository: AuthRepository,
    private readonly jwtService: JwtService,
  ) {}

  async register({
    username,
    password,
  }: Credentials): Promise<RegisterResponse> {
    const passwordHash = await bcrypt.hash(password, BCRYPT_ROUNDS);
    if (!(await this.repository.createUser({ username, passwordHash }))) {
      throw new ConflictException('Username already registered');
    }
    return { username };
  }

  async login({ username, password }: Credentials): Promise<TokenResponse> {
    const user = await this.repository.findUser(username);

    // A malformed stored hash counts as a mismatch
    const matches = await bcrypt
      .compare(password, user?.passwordHash ?? DUMMY_HASH)
      .catch(() => false);
    if (user === null || !matches) {
      throw new UnauthorizedException('Invalid username or password');
    }

    const refreshToken = randomBytes(32).toString('base64url');
    await this.repository.saveRefreshToken(
      hashRefreshToken(refreshToken),
      user.username,
      REFRESH_TOKEN_SECONDS,
    );
    return this.tokenResponse(user.username, refreshToken);
  }

  async refresh(refreshToken: string): Promise<TokenResponse> {
    const next = randomBytes(32).toString('base64url');
    const username = await this.repository.rotateRefreshToken(
      hashRefreshToken(refreshToken),
      hashRefreshToken(next),
      REFRESH_TOKEN_SECONDS,
    );
    if (username === null) {
      throw new UnauthorizedException('Invalid or expired refresh token');
    }
    return this.tokenResponse(username, next);
  }

  async logout(refreshToken: string): Promise<void> {
    await this.repository.revokeRefreshToken(hashRefreshToken(refreshToken));
  }

  private tokenResponse(username: string, refreshToken: string): TokenResponse {
    return {
      accessToken: this.jwtService.generateAccessToken(username),
      refreshToken,
      tokenType: 'Bearer',
      expiresIn: ACCESS_TOKEN_SECONDS,
    };
  }
}
