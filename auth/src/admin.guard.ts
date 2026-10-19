import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import type { Request } from 'express';
import { AccessTokenClaims, JwtService } from './jwt.service.js';

/** A request the AdminGuard let through, carrying the admin's verified claims for logging. */
export interface AdminRequest extends Request {
  admin: AccessTokenClaims;
}

/**
 * Lets a request through only with a valid access token that has the ADMIN role:
 * 401 for a missing, forged, expired or malformed token, 403 for a valid token without ADMIN.
 */
@Injectable()
export class AdminGuard implements CanActivate {
  constructor(private readonly jwtService: JwtService) {}

  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<AdminRequest>();
    const [scheme, token] = request.headers.authorization?.split(' ') ?? [];
    if (scheme !== 'Bearer' || !token) {
      throw new UnauthorizedException('Missing access token');
    }

    let claims: AccessTokenClaims;
    try {
      claims = this.jwtService.verifyAccessToken(token);
    } catch {
      // One message for every bad token, so the response does not say what was wrong with it
      throw new UnauthorizedException('Invalid or expired access token');
    }

    if (!claims.roles.includes('ADMIN')) {
      throw new ForbiddenException('Admin role required');
    }
    request.admin = claims;
    return true;
  }
}
