import {
  ExecutionContext,
  ForbiddenException,
  UnauthorizedException,
} from '@nestjs/common';
import jwt from 'jsonwebtoken';
import { AdminGuard, AdminRequest } from './admin.guard.js';
import { JwtService } from './jwt.service.js';

const SECRET = '0123456789abcdef0123456789abcdef';

describe('AdminGuard', () => {
  const jwtService = new JwtService(SECRET);
  const guard = new AdminGuard(jwtService);

  /** A fake Nest context around a request with the given Authorization header. */
  const contextWith = (authorization?: string) => {
    const request = { headers: { authorization } } as AdminRequest;
    const context = {
      switchToHttp: () => ({ getRequest: () => request }),
    } as ExecutionContext;
    return { context, request };
  };

  it('lets an admin through and attaches their claims to the request', () => {
    const token = jwtService.generateAccessToken('test_admin', ['ADMIN']);
    const { context, request } = contextWith(`Bearer ${token}`);

    expect(guard.canActivate(context)).toBe(true);
    expect(request.admin).toEqual({ sub: 'test_admin', roles: ['ADMIN'] });
  });

  it('lets through a user who is an admin and a trader', () => {
    const token = jwtService.generateAccessToken('test_admin', [
      'ADMIN',
      'TRADER',
    ]);

    expect(guard.canActivate(contextWith(`Bearer ${token}`).context)).toBe(
      true,
    );
  });

  it('refuses a valid token without ADMIN with 403', () => {
    const token = jwtService.generateAccessToken('john_doe1', ['TRADER']);

    expect(() =>
      guard.canActivate(contextWith(`Bearer ${token}`).context),
    ).toThrow(ForbiddenException);
  });

  it.each([
    ['no Authorization header', undefined],
    ['an empty header', ''],
    ['another scheme', 'Basic dXNlcjpwYXNz'],
    ['Bearer with no token', 'Bearer '],
  ])('refuses %s with 401', (_, header) => {
    expect(() => guard.canActivate(contextWith(header).context)).toThrow(
      new UnauthorizedException('Missing access token'),
    );
  });

  it.each([
    [
      'a forged token',
      jwt.sign(
        { sub: 'x', roles: ['ADMIN'] },
        'another-secret-another-secret-123',
      ),
    ],
    [
      'an expired token',
      jwt.sign({ sub: 'x', roles: ['ADMIN'] }, SECRET, { expiresIn: -10 }),
    ],
    ['a malformed token', jwt.sign({ sub: 'x' }, SECRET)],
    ['garbage', 'not-a-jwt'],
  ])('refuses %s with the same 401', (_, token) => {
    expect(() =>
      guard.canActivate(contextWith(`Bearer ${token}`).context),
    ).toThrow(new UnauthorizedException('Invalid or expired access token'));
  });
});
