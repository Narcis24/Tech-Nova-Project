import { Injectable } from '@nestjs/common';
import jwt from 'jsonwebtoken';

export const ACCESS_TOKEN_SECONDS = 900;

@Injectable()
export class JwtService {
  private readonly secret: string;

  /** Fails startup on a missing or weak secret instead of on the first request. HS256 needs 32+ bytes. */
  constructor(secret: string | undefined = process.env.JWT_SECRET) {
    if (secret === undefined || secret.trim().length === 0) {
      throw new Error('JWT_SECRET is not set');
    }
    if (Buffer.byteLength(secret, 'utf8') < 32) {
      throw new Error('JWT_SECRET must be at least 32 bytes');
    }
    this.secret = secret;
  }

  /** Claims are exactly sub, roles, iat and exp; the header is {"alg":"HS256","typ":"JWT"}. */
  generateAccessToken(username: string): string {
    return jwt.sign({ sub: username, roles: ['TRADER'] }, this.secret, {
      algorithm: 'HS256',
      expiresIn: ACCESS_TOKEN_SECONDS,
    });
  }
}
