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
    const token = new JwtService(SECRET).generateAccessToken('john_doe1', [
      'TRADER',
    ]);
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

  it('puts every role the user has into the token', () => {
    const token = new JwtService(SECRET).generateAccessToken('john_doe1', [
      'ADMIN',
      'TRADER',
    ]);
    const claims = jwt.decode(token) as jwt.JwtPayload;

    expect(claims.roles).toEqual(['ADMIN', 'TRADER']);
  });

  it('uses HS256 even with a long secret', () => {
    const token = new JwtService('x'.repeat(64)).generateAccessToken(
      'john_doe1',
      ['TRADER'],
    );
    expect(jwt.decode(token, { complete: true })!.header.alg).toBe('HS256');
  });

  describe('verifyAccessToken', () => {
    const service = new JwtService(SECRET);
    const base64url = (part: object) =>
      Buffer.from(JSON.stringify(part)).toString('base64url');

    it('returns sub and roles from a token it issued', () => {
      const token = service.generateAccessToken('john_doe1', ['ADMIN']);

      expect(service.verifyAccessToken(token)).toEqual({
        sub: 'john_doe1',
        roles: ['ADMIN'],
      });
    });

    it('rejects a token signed with another secret', () => {
      const forged = jwt.sign(
        { sub: 'john_doe1', roles: ['ADMIN'] },
        'another-secret-another-secret-123',
        { algorithm: 'HS256' },
      );

      expect(() => service.verifyAccessToken(forged)).toThrow();
    });

    it('rejects a token whose payload was edited after signing', () => {
      const [header, , signature] = service
        .generateAccessToken('john_doe1', ['TRADER'])
        .split('.');
      const promoted = base64url({ sub: 'john_doe1', roles: ['ADMIN'] });

      expect(() =>
        service.verifyAccessToken(`${header}.${promoted}.${signature}`),
      ).toThrow();
    });

    it('rejects an expired token', () => {
      const expired = jwt.sign({ sub: 'john_doe1', roles: ['ADMIN'] }, SECRET, {
        algorithm: 'HS256',
        expiresIn: -10,
      });

      expect(() => service.verifyAccessToken(expired)).toThrow(/expired/);
    });

    it('rejects an unsigned token (alg none)', () => {
      const unsigned = `${base64url({ alg: 'none', typ: 'JWT' })}.${base64url({
        sub: 'john_doe1',
        roles: ['ADMIN'],
      })}.`;

      expect(() => service.verifyAccessToken(unsigned)).toThrow();
    });

    it.each([
      ['no roles', { sub: 'john_doe1' }],
      ['roles that are not strings', { sub: 'john_doe1', roles: [1] }],
      ['roles that are not a list', { sub: 'john_doe1', roles: 'ADMIN' }],
      ['no sub', { roles: ['ADMIN'] }],
    ])('rejects a genuine token with %s', (_, claims) => {
      const token = jwt.sign(claims, SECRET, { algorithm: 'HS256' });

      expect(() => service.verifyAccessToken(token)).toThrow(
        'Malformed access token',
      );
    });
  });
});
