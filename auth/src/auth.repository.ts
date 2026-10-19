import {
  Injectable,
  Logger,
  OnModuleDestroy,
  OnModuleInit,
} from '@nestjs/common';
import pg from 'pg';

/** Who an access token is for: its sub and roles claims. */
export interface TokenOwner {
  username: string;
  roles: string[];
}

export interface User extends TokenOwner {
  /** Null for an account an admin created whose owner has not set a password yet. */
  passwordHash: string | null;
}

// ARRAY(...) rather than array_agg: a user with no role gets an empty list, not null
const SELECT_USER = `SELECT username, password_hash,
  ARRAY(SELECT role FROM user_roles r WHERE r.username = users.username ORDER BY role) AS roles
  FROM users`;

export interface NewUser {
  username: string;
  passwordHash: string;
  email: string;
}

@Injectable()
export class AuthRepository implements OnModuleInit, OnModuleDestroy {
  private readonly pool = new pg.Pool({
    host: process.env.DB_HOST ?? 'localhost',
    port: Number(process.env.DB_PORT ?? 5434),
    database: process.env.DB_NAME ?? 'technova',
    user: process.env.DB_USER ?? 'technova',
    password: process.env.DB_PASSWORD,
  });
  private readonly logger = new Logger(AuthRepository.name);

  constructor() {
    // Without a listener, an idle connection dropped by Postgres (e.g. a restart) crashes the process
    this.pool.on('error', (err) =>
      this.logger.error(`Idle database connection failed: ${err.message}`),
    );
  }

  /** Fails startup if the database is unreachable or a table or column is missing (run migrations V010, V012 and V013). */
  async onModuleInit() {
    if (process.env.DB_PASSWORD === undefined) {
      throw new Error('DB_PASSWORD is not set');
    }
    await this.pool.query(
      'SELECT username, password_hash, email FROM users LIMIT 0',
    );
    await this.pool.query('SELECT username, role FROM user_roles LIMIT 0');
    await this.pool.query(
      'SELECT token_hash, username, expires_at, revoked_at FROM refresh_tokens LIMIT 0',
    );
    await this.pool.query(
      'SELECT token_hash, username, expires_at, used_at FROM password_reset_tokens LIMIT 0',
    );
  }

  async onModuleDestroy() {
    await this.pool.end();
  }

  async findUser(username: string): Promise<User | null> {
    return this.findOneUser(`${SELECT_USER} WHERE username = $1`, username);
  }

  /** Ignores the case of the email. */
  async findUserByEmail(email: string): Promise<User | null> {
    return this.findOneUser(
      `${SELECT_USER} WHERE LOWER(email) = LOWER($1)`,
      email,
    );
  }

  /** Registers the user as a TRADER. False if the username or the email (ignoring case) is taken. */
  async createUser(user: NewUser): Promise<boolean> {
    // One statement, so a user is never left without a role
    const { rowCount } = await this.pool.query(
      `WITH created AS (
         INSERT INTO users (username, password_hash, email) VALUES ($1, $2, $3)
         ON CONFLICT DO NOTHING
         RETURNING username
       )
       INSERT INTO user_roles (username, role) SELECT username, 'TRADER' FROM created`,
      [user.username, user.passwordHash, user.email],
    );
    return rowCount === 1;
  }

  private async findOneUser(sql: string, param: string): Promise<User | null> {
    const { rows } = await this.pool.query<{
      username: string;
      password_hash: string | null;
      roles: string[];
    }>(sql, [param]);
    return rows.length === 0
      ? null
      : {
          username: rows[0].username,
          passwordHash: rows[0].password_hash,
          roles: rows[0].roles,
        };
  }

  async saveRefreshToken(
    tokenHash: string,
    username: string,
    ttlSeconds: number,
  ) {
    await this.pool.query(
      "INSERT INTO refresh_tokens (token_hash, username, expires_at) VALUES ($1, $2, now() + $3 * interval '1 second')",
      [tokenHash, username, ttlSeconds],
    );
  }

  /**
   * Revokes a usable refresh token and stores its replacement, in one statement so a token
   * can be used only once. Returns the owner, or null if the old token was not usable.
   */
  async rotateRefreshToken(
    oldHash: string,
    newHash: string,
    ttlSeconds: number,
  ): Promise<TokenOwner | null> {
    const { rows } = await this.pool.query<TokenOwner>(
      `WITH used AS (
         UPDATE refresh_tokens SET revoked_at = now()
         WHERE token_hash = $1 AND revoked_at IS NULL AND expires_at > now()
         RETURNING username
       ),
       issued AS (
         INSERT INTO refresh_tokens (token_hash, username, expires_at)
         SELECT $2, username, now() + $3 * interval '1 second' FROM used
         RETURNING username
       )
       SELECT username,
              ARRAY(SELECT role FROM user_roles r WHERE r.username = issued.username ORDER BY role) AS roles
       FROM issued`,
      [oldHash, newHash, ttlSeconds],
    );
    return rows.length === 0 ? null : rows[0];
  }

  async revokeRefreshToken(tokenHash: string) {
    await this.pool.query(
      'UPDATE refresh_tokens SET revoked_at = now() WHERE token_hash = $1 AND revoked_at IS NULL',
      [tokenHash],
    );
  }

  /** Stores a new reset token for the user; any earlier unused one stops working. */
  async saveResetToken(
    tokenHash: string,
    username: string,
    ttlSeconds: number,
  ) {
    await this.pool.query(
      `WITH cancelled AS (
         UPDATE password_reset_tokens SET used_at = now()
         WHERE username = $2 AND used_at IS NULL
       )
       INSERT INTO password_reset_tokens (token_hash, username, expires_at)
       VALUES ($1, $2, now() + $3 * interval '1 second')`,
      [tokenHash, username, ttlSeconds],
    );
  }

  /**
   * Uses a reset token, sets the new password and signs the user out everywhere, in one statement
   * so a token works only once. False if the token was unknown, used or expired.
   */
  async resetPassword(
    tokenHash: string,
    passwordHash: string,
  ): Promise<boolean> {
    const { rows } = await this.pool.query<{ username: string }>(
      `WITH used AS (
         UPDATE password_reset_tokens SET used_at = now()
         WHERE token_hash = $1 AND used_at IS NULL AND expires_at > now()
         RETURNING username
       ),
       updated AS (
         UPDATE users SET password_hash = $2
         FROM used WHERE users.username = used.username
         RETURNING users.username
       ),
       revoked AS (
         UPDATE refresh_tokens SET revoked_at = now()
         FROM updated
         WHERE refresh_tokens.username = updated.username AND refresh_tokens.revoked_at IS NULL
       )
       SELECT username FROM updated`,
      [tokenHash, passwordHash],
    );
    return rows.length === 1;
  }
}
