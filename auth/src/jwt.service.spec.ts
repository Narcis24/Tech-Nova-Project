import jwt from 'jsonwebtoken';
import { JwtService } from './jwt.service.js';

const SECRET = '0123456789abcdef0123456789abcdef';

describe('JwtService', () => {
  it('rejects a missing secret', () => {
    expect(() => new JwtService('')).toThrow();
    expect(() => new JwtService('   ')).toThrow();
    expect(() => new JwtService(undefined)).toThrow();
  });

  it('rejects a secret shorter than 32 bytes', () => {
    expect(() => new JwtService('too-short-secret')).toThrow();
  });

  it('issues an HS256 token with exactly sub, roles, iat and exp, valid for 15 minutes', () => {
    const token = new JwtService(SECRET).generateAccessToken('john_doe1');
    const { header, payload } = jwt.decode(token, { complete: true })!;
    const claims = payload as jwt.JwtPayload;

    expect(header).toEqual({ alg: 'HS256', typ: 'JWT' });
    expect(Object.keys(claims).sort()).toEqual(['exp', 'iat', 'roles', 'sub']);
    expect(claims.sub).toBe('john_doe1');
    expect(claims.roles).toEqual(['TRADER']);
    expect(claims.exp! - claims.iat!).toBe(900);
    expect(() =>
      jwt.verify(token, SECRET, { algorithms: ['HS256'] }),
    ).not.toThrow();
  });

  it('uses HS256 even with a long secret', () => {
    const token = new JwtService('x'.repeat(64)).generateAccessToken(
      'john_doe1',
    );
    expect(jwt.decode(token, { complete: true })!.header.alg).toBe('HS256');
  });
});
