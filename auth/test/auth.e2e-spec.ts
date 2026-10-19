import { Test } from '@nestjs/testing';
import type { NestExpressApplication } from '@nestjs/platform-express';
import jwt from 'jsonwebtoken';
import request from 'supertest';
import { App } from 'supertest/types.js';
import { AppModule } from './../src/app.module.js';
import { AuthRepository, NewUser } from './../src/auth.repository.js';
import { configure } from './../src/configure.js';
import { JwtService } from './../src/jwt.service.js';
import { MailService } from './../src/mail.service.js';

const SECRET = '0123456789abcdef0123456789abcdef';

/** In-memory stand-in for the users, user_roles, refresh_tokens and password_reset_tokens tables, with the same rules as the SQL. */
class FakeAuthRepository {
  readonly users = new Map<string, NewUser & { roles: string[] }>();
  readonly tokens = new Map<
    string,
    { username: string; expiresAt: number; revoked: boolean }
  >();

  async findUser(username: string) {
    return this.users.get(username) ?? null;
  }
  async findUserByEmail(email: string) {
    for (const user of this.users.values()) {
      if (user.email.toLowerCase() === email.toLowerCase()) return user;
    }
    return null;
  }
  async createUser(user: NewUser) {
    if (
      this.users.has(user.username) ||
      (await this.findUserByEmail(user.email))
    )
      return false;
    this.users.set(user.username, { ...user, roles: ['TRADER'] });
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
    return {
      username: old.username,
      roles: this.users.get(old.username)?.roles ?? [],
    };
  }
  async revokeRefreshToken(hash: string) {
    const token = this.tokens.get(hash);
    if (token) token.revoked = true;
  }

  readonly resetTokens = new Map<
    string,
    { username: string; expiresAt: number; used: boolean }
  >();
  async saveResetToken(hash: string, username: string, ttl: number) {
    for (const token of this.resetTokens.values()) {
      if (token.username === username) token.used = true;
    }
    this.resetTokens.set(hash, {
      username,
      expiresAt: Date.now() + ttl * 1000,
      used: false,
    });
  }
  async resetPassword(tokenHash: string, passwordHash: string) {
    const token = this.resetTokens.get(tokenHash);
    if (!token || token.used || token.expiresAt <= Date.now()) return false;
    token.used = true;
    this.users.get(token.username)!.passwordHash = passwordHash;
    for (const refresh of this.tokens.values()) {
      if (refresh.username === token.username) refresh.revoked = true;
    }
    return true;
  }
}

/** Keeps the emails instead of sending them. */
class FakeMailService {
  readonly sent: { to: string; link: string }[] = [];
  async sendPasswordReset(to: string, link: string) {
    this.sent.push({ to, link });
  }
}

const VALID = { username: 'john_doe1', password: 'Something123@' };
const EMAIL = 'john.doe@example.com';
const REGISTRATION = { ...VALID, email: EMAIL };

describe('Auth (e2e)', () => {
  let app: NestExpressApplication;
  let repository: FakeAuthRepository;
  let mail: FakeMailService;

  beforeEach(async () => {
    repository = new FakeAuthRepository();
    mail = new FakeMailService();
    const moduleFixture = await Test.createTestingModule({
      imports: [AppModule],
    })
      .overrideProvider(AuthRepository)
      .useValue(repository)
      .overrideProvider(JwtService)
      .useValue(new JwtService(SECRET))
      .overrideProvider(MailService)
      .useValue(mail)
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
    await post('register').send(REGISTRATION).expect(201);
    return (await post('login').send(VALID).expect(200)).body as {
      accessToken: string;
      refreshToken: string;
    };
  };

  describe('register', () => {
    it('returns 201 with the username', async () => {
      const res = await post('register').send(REGISTRATION).expect(201);
      expect(res.body).toEqual({ username: 'john_doe1' });
    });

    it('accepts any 12-20 character password', async () => {
      const res = await post('register')
        .send({ username: 'john_doe1', password: 'alllowercase', email: EMAIL })
        .expect(201);
      expect(res.body).toEqual({ username: 'john_doe1' });
    });

    it('rejects a taken username with 409', async () => {
      await post('register').send(REGISTRATION).expect(201);
      const res = await post('register')
        .send({ ...REGISTRATION, email: 'other@example.com' })
        .expect(409);
      expect(res.body).toEqual({
        statusCode: 409,
        error: 'Conflict',
        message: 'Username or email already registered',
      });
    });

    it('rejects a taken email, ignoring case, with the same 409', async () => {
      await post('register').send(REGISTRATION).expect(201);
      const res = await post('register')
        .send({
          ...REGISTRATION,
          username: 'jane_doe1',
          email: 'John.Doe@Example.COM',
        })
        .expect(409);
      expect(res.body.message).toBe('Username or email already registered');
    });

    it('requires a valid email', async () => {
      const res = await post('register')
        .send({ ...VALID, email: 'not-an-email' })
        .expect(400);
      expect(res.body.message).toEqual(['email must be an email']);
    });
  });

  describe('login', () => {
    it('returns an access token and a refresh token', async () => {
      await post('register').send(REGISTRATION).expect(201);
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

    it('puts every role of the user into the token, and refresh picks up a role change', async () => {
      await post('register').send(REGISTRATION).expect(201);
      repository.users.get('john_doe1')!.roles = ['ADMIN', 'TRADER'];

      const login = await post('login').send(VALID).expect(200);
      expect(jwt.decode(login.body.accessToken)).toMatchObject({
        roles: ['ADMIN', 'TRADER'],
      });

      repository.users.get('john_doe1')!.roles = ['TRADER'];
      const refreshed = await post('refresh')
        .send({ refreshToken: login.body.refreshToken })
        .expect(200);
      expect(jwt.decode(refreshed.body.accessToken)).toMatchObject({
        roles: ['TRADER'],
      });
    });

    it('gives an identical 401 for a wrong password and an unknown user', async () => {
      await post('register').send(REGISTRATION).expect(201);
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
      const rejected = {
        statusCode: 401,
        error: 'Unauthorized',
        message: 'Invalid or expired refresh token',
      };
      const unknown = await post('refresh')
        .send({ refreshToken: 'unknown' })
        .expect(401);
      expect(unknown.body).toEqual(rejected);

      const { refreshToken } = await loggedIn();
      for (const token of repository.tokens.values())
        token.expiresAt = Date.now() - 1;
      const expired = await post('refresh').send({ refreshToken }).expect(401);
      expect(expired.body).toEqual(rejected);
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
      const unknown = await post('logout')
        .send({ refreshToken: 'unknown' })
        .expect(204);
      expect(unknown.text).toBe('');
      const { refreshToken } = await loggedIn();
      await post('logout').send({ refreshToken }).expect(204);
      const revoked = await post('logout').send({ refreshToken }).expect(204);
      expect(revoked.text).toBe('');
    });
  });

  describe('password reset', () => {
    const NEW_PASSWORD = 'NewPassword123';

    // forgot-password answers before the email is sent, so wait for it
    const resetToken = async () => {
      await vi.waitFor(() => expect(mail.sent).toHaveLength(1));
      return mail.sent[0].link.split('#token=')[1];
    };

    it('answers 202 and sends nothing for an email no account uses', async () => {
      const res = await post('forgot-password')
        .send({ email: 'nobody@example.com' })
        .expect(202);
      expect(res.text).toBe('');
      await new Promise((resolve) => setTimeout(resolve, 50));
      expect(mail.sent).toEqual([]);
    });

    it('emails a link that sets a new password once and signs the user out everywhere', async () => {
      const { refreshToken } = await loggedIn();

      await post('forgot-password')
        .send({ email: 'JOHN.DOE@example.com' })
        .expect(202);
      const token = await resetToken();
      expect(mail.sent[0].to).toBe('JOHN.DOE@example.com');
      expect(mail.sent[0].link).toBe(
        `http://localhost:4200/reset-password#token=${token}`,
      );

      const res = await post('reset-password')
        .send({ token, newPassword: NEW_PASSWORD })
        .expect(204);
      expect(res.text).toBe('');

      await post('login').send(VALID).expect(401);
      await post('login')
        .send({ ...VALID, password: NEW_PASSWORD })
        .expect(200);
      await post('refresh').send({ refreshToken }).expect(401);

      const again = await post('reset-password')
        .send({ token, newPassword: 'AnotherPass123' })
        .expect(400);
      expect(again.body.message).toBe('Invalid or expired reset link');
    });

    it('stores only a hash of the reset token', async () => {
      await post('register').send(REGISTRATION).expect(201);
      await post('forgot-password').send({ email: EMAIL }).expect(202);
      const token = await resetToken();

      expect(repository.resetTokens.has(token)).toBe(false);
    });

    it('rejects an unknown or expired reset token with 400', async () => {
      const unknown = await post('reset-password')
        .send({ token: 'unknown', newPassword: NEW_PASSWORD })
        .expect(400);
      expect(unknown.body.message).toBe('Invalid or expired reset link');

      await post('register').send(REGISTRATION).expect(201);
      await post('forgot-password').send({ email: EMAIL }).expect(202);
      const token = await resetToken();
      for (const reset of repository.resetTokens.values())
        reset.expiresAt = Date.now() - 1;
      await post('reset-password')
        .send({ token, newPassword: NEW_PASSWORD })
        .expect(400);
    });

    it('validates the request bodies', async () => {
      const forgot = await post('forgot-password')
        .send({ email: 'not-an-email' })
        .expect(400);
      expect(forgot.body.message).toEqual(['email must be an email']);

      const reset = await post('reset-password')
        .send({ token: 'abc', newPassword: 'short' })
        .expect(400);
      expect(reset.body.message).toEqual([
        'newPassword must be longer than or equal to 12 characters',
      ]);
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
        .send({
          username: 'a'.repeat(17),
          password: 'a'.repeat(21),
          email: EMAIL,
        })
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
        .send({ ...REGISTRATION, role: 'ADMIN' })
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
      const res = await http().get('/auth/health').expect(200);
      expect(res.body).toEqual({ status: 'ok' });
    });

    it('serves the OpenAPI document with the contract paths', async () => {
      const res = await http().get('/auth/docs-json').expect(200);
      expect(res.body.servers).toEqual([{ url: '/auth' }]);
      expect(Object.keys(res.body.paths).sort()).toEqual([
        '/v1/auth/forgot-password',
        '/v1/auth/login',
        '/v1/auth/logout',
        '/v1/auth/refresh',
        '/v1/auth/register',
        '/v1/auth/reset-password',
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
