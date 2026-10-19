import { Injectable } from '@nestjs/common';
import jwt from 'jsonwebtoken';

export const ACCESS_TOKEN_SECONDS = 900;

/** What a verified access token says about its holder */
export interface AccessTokenClaims {
  sub: string;
  roles: string[];
}

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
  generateAccessToken(username: string, roles: string[]): string {
    return jwt.sign({ sub: username, roles }, this.secret, {
      algorithm: 'HS256',
      expiresIn: ACCESS_TOKEN_SECONDS,
    });
  }

  /** Throws if the token is forged, expired, not HS256, or missing sub or roles. */
  verifyAccessToken(token: string): AccessTokenClaims {
    const payload = jwt.verify(token, this.secret, { algorithms: ['HS256'] });

    if (
      typeof payload === 'string' || // a string isn't our token format
      typeof payload.sub !== 'string' || // must say who the token is for
      !Array.isArray(payload.roles) || // roles must be a list...
      !payload.roles.every((role) => typeof role === 'string') // ...of strings only
    ) {
      throw new Error('Malformed access token');
    }
    return { sub: payload.sub, roles: payload.roles };
  }
}
