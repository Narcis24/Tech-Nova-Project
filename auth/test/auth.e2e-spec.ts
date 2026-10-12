import { Test } from '@nestjs/testing';
import type { NestExpressApplication } from '@nestjs/platform-express';
import jwt from 'jsonwebtoken';
import request from 'supertest';
import { App } from 'supertest/types.js';
import { AppModule } from './../src/app.module.js';
import { AuthRepository, User } from './../src/auth.repository.js';
import { configure } from './../src/configure.js';
import { JwtService } from './../src/jwt.service.js';

const SECRET = '0123456789abcdef0123456789abcdef';

/** In-memory stand-in for the users and refresh_tokens tables, with the same rules as the SQL. */
class FakeAuthRepository {
  readonly users = new Map<string, User>();
  readonly tokens = new Map<
    string,
    { username: string; expiresAt: number; revoked: boolean }
  >();

  async findUser(username: string) {
    return this.users.get(username) ?? null;
  }
  async createUser(user: User) {
    if (this.users.has(user.username)) return false;
    this.users.set(user.username, user);
    return true;
  }
  async saveRefreshToken(hash: string, username: string, ttl: number) {
    this.tokens.set(hash, {
      username,
      expiresAt: Date.now() + ttl * 1000,
      revoked: false,
    });
  }
  async rotateRefreshToken(oldHash: string, newHash: string, ttl: number) {
    const old = this.tokens.get(oldHash);
    if (!old || old.revoked || old.expiresAt <= Date.now()) return null;
    old.revoked = true;
    await this.saveRefreshToken(newHash, old.username, ttl);
    return old.username;
  }
  async revokeRefreshToken(hash: string) {
    const token = this.tokens.get(hash);
    if (token) token.revoked = true;
  }
}

const VALID = { username: 'john_doe1', password: 'Something123@' };

describe('Auth (e2e)', () => {
  let app: NestExpressApplication;
  let repository: FakeAuthRepository;

  beforeEach(async () => {
    repository = new FakeAuthRepository();
    const moduleFixture = await Test.createTestingModule({
      imports: [AppModule],
    })
      .overrideProvider(AuthRepository)
      .useValue(repository)
      .overrideProvider(JwtService)
      .useValue(new JwtService(SECRET))
      .compile();

    app = moduleFixture.createNestApplication<NestExpressApplication>();
    configure(app);
    await app.init();
  });

  afterEach(async () => {
    await app.close();
  });

  const http = () => request(app.getHttpServer() as App);
  const post = (path: string) => http().post(`/auth/v1/auth/${path}`);
  const loggedIn = async () => {
    await post('register').send(VALID).expect(201);
    return (await post('login').send(VALID).expect(200)).body as {
      accessToken: string;
      refreshToken: string;
    };
  };

  describe('register', () => {
    it('returns 201 with the username', async () => {
      const res = await post('register').send(VALID).expect(201);
      expect(res.body).toEqual({ username: 'john_doe1' });
    });

    it('accepts any 12-20 character password', async () => {
      await post('register')
        .send({ username: 'john_doe1', password: 'alllowercase' })
        .expect(201);
    });

    it('rejects a taken username with 409', async () => {
      await post('register').send(VALID).expect(201);
      const res = await post('register').send(VALID).expect(409);
      expect(res.body).toEqual({
        statusCode: 409,
        error: 'Conflict',
        message: 'Username already registered',
      });
    });
  });

  describe('login', () => {
    it('returns an access token and a refresh token', async () => {
      await post('register').send(VALID).expect(201);
      const res = await post('login').send(VALID).expect(200);

      expect(res.body).toEqual({
        accessToken: expect.any(String),
        refreshToken: expect.any(String),
        tokenType: 'Bearer',
        expiresIn: 900,
      });
      const claims = jwt.verify(res.body.accessToken, SECRET, {
        algorithms: ['HS256'],
      }) as jwt.JwtPayload;
      expect(claims).toEqual({
        sub: 'john_doe1',
        roles: ['TRADER'],
        iat: expect.any(Number),
        exp: claims.iat! + 900,
      });
    });

    it('gives an identical 401 for a wrong password and an unknown user', async () => {
      await post('register').send(VALID).expect(201);
      const expected = {
        statusCode: 401,
        error: 'Unauthorized',
        message: 'Invalid username or password',
      };

      const wrong = await post('login')
        .send({ ...VALID, password: 'Wrong-Password1' })
        .expect(401);
      const unknown = await post('login')
        .send({ ...VALID, username: 'nobody_123' })
        .expect(401);
      expect(wrong.body).toEqual(expected);
      expect(unknown.body).toEqual(expected);
    });
  });

  describe('refresh', () => {
    it('rotates: the new token works, the used one does not', async () => {
      const { refreshToken } = await loggedIn();

      const res = await post('refresh').send({ refreshToken }).expect(200);
      expect(res.body).toEqual({
        accessToken: expect.any(String),
        refreshToken: expect.any(String),
        tokenType: 'Bearer',
        expiresIn: 900,
      });
      expect(res.body.refreshToken).not.toBe(refreshToken);

      const reused = await post('refresh').send({ refreshToken }).expect(401);
      expect(reused.body).toEqual({
        statusCode: 401,
        error: 'Unauthorized',
        message: 'Invalid or expired refresh token',
      });
      await post('refresh')
        .send({ refreshToken: res.body.refreshToken })
        .expect(200);
    });

    it('rejects an unknown or expired token', async () => {
      await post('refresh').send({ refreshToken: 'unknown' }).expect(401);

      const { refreshToken } = await loggedIn();
      for (const token of repository.tokens.values())
        token.expiresAt = Date.now() - 1;
      await post('refresh').send({ refreshToken }).expect(401);
    });

    it('does not store the token itself', async () => {
      const { refreshToken } = await loggedIn();
      expect(repository.tokens.has(refreshToken)).toBe(false);
    });
  });

  describe('logout', () => {
    it('revokes the refresh token with 204', async () => {
      const { refreshToken } = await loggedIn();

      const res = await post('logout').send({ refreshToken }).expect(204);
      expect(res.text).toBe('');
      await post('refresh').send({ refreshToken }).expect(401);
    });

    it('returns 204 for an unknown or already revoked token', async () => {
      await post('logout').send({ refreshToken: 'unknown' }).expect(204);
      const { refreshToken } = await loggedIn();
      await post('logout').send({ refreshToken }).expect(204);
      await post('logout').send({ refreshToken }).expect(204);
    });
  });

  describe('validation', () => {
    const badRequest = (message: unknown) => ({
      statusCode: 400,
      error: 'Bad Request',
      message,
    });

    it('lists one message per broken rule', async () => {
      const res = await post('login')
        .send({ username: 'ab-', password: 'short' })
        .expect(400);
      expect(res.body).toEqual(
        badRequest([
          'username must match /^[A-Za-z0-9_]+$/ regular expression',
          'username must be longer than or equal to 8 characters',
          'password must be longer than or equal to 12 characters',
        ]),
      );
    });

    it('rejects too long values', async () => {
      const res = await post('register')
        .send({ username: 'a'.repeat(17), password: 'a'.repeat(21) })
        .expect(400);
      expect(res.body.message).toEqual([
        'username must be shorter than or equal to 16 characters',
        'password must be shorter than or equal to 20 characters',
      ]);
    });

    it('rejects missing and non-string fields', async () => {
      const res = await post('register')
        .send({ username: 12345678 })
        .expect(400);
      expect(res.body.message).toEqual(
        expect.arrayContaining([
          'username must be a string',
          'password must be a string',
        ]),
      );
    });

    it('rejects unknown properties', async () => {
      const res = await post('register')
        .send({ ...VALID, role: 'ADMIN' })
        .expect(400);
      expect(res.body).toEqual(badRequest(['property role should not exist']));
    });

    it('rejects an empty refresh token', async () => {
      const res = await post('refresh').send({ refreshToken: '' }).expect(400);
      expect(res.body.message).toEqual([
        'refreshToken must be longer than or equal to 1 characters',
      ]);
      await post('logout').send({}).expect(400);
    });

    it.each([
      ['invalid JSON', '{"username":'],
      ['no body', ''],
      ['a JSON array', '[]'],
      ['a JSON null', 'null'],
    ])('rejects %s', async (_, body) => {
      const res = await post('login')
        .set('Content-Type', 'application/json')
        .send(body)
        .expect(400);
      expect(res.body).toMatchObject({ statusCode: 400, error: 'Bad Request' });
      expect(res.body.message).toBeDefined();
    });
  });

  describe('operational endpoints', () => {
    it('reports health', async () => {
      await http().get('/auth/health').expect(200);
    });

    it('serves the OpenAPI document with the contract paths', async () => {
      const res = await http().get('/auth/docs-json').expect(200);
      expect(res.body.servers).toEqual([{ url: '/auth' }]);
      expect(Object.keys(res.body.paths).sort()).toEqual([
        '/v1/auth/login',
        '/v1/auth/logout',
        '/v1/auth/refresh',
        '/v1/auth/register',
      ]);
      expect(res.body.paths['/v1/auth/login'].post.operationId).toBe('login');
    });

    it('serves Swagger UI at /auth/docs and redirects the old URLs', async () => {
      const res = await http().get('/auth/docs').expect(200);
      expect(res.text).toContain('swagger-ui');
      await http()
        .get('/auth/swagger-ui.html')
        .expect(302)
        .expect('Location', '/auth/docs');
      await http()
        .get('/auth/v3/api-docs')
        .expect(302)
        .expect('Location', '/auth/docs-json');
    });
  });
});
