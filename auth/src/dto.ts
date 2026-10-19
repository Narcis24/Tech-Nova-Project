import { ApiProperty } from '@nestjs/swagger';
import {
  IsEmail,
  IsString,
  Matches,
  MaxLength,
  MinLength,
} from 'class-validator';

export class Credentials {
  @ApiProperty({
    minLength: 8,
    maxLength: 16,
    pattern: '^[A-Za-z0-9_]+$',
    description: 'Letters, digits and underscore',
    example: 'john_doe1',
  })
  @IsString()
  @MinLength(8)
  @MaxLength(16)
  // spelled out rather than \w: the pattern is quoted in the public 400 message and the contract
  @Matches(/^[A-Za-z0-9_]+$/) // NOSONAR
  username: string;

  @ApiProperty({
    format: 'password',
    minLength: 12,
    maxLength: 20,
    example: 'Something123@',
  })
  @IsString()
  @MinLength(12)
  @MaxLength(20)
  password: string;
}

/** Login keeps Credentials; registering also takes the email that password reset links go to. */
export class RegisterRequest extends Credentials {
  @ApiProperty({
    format: 'email',
    maxLength: 254,
    example: 'john.doe@example.com',
  })
  @IsEmail()
  @MaxLength(254)
  email: string;
}

export class ForgotPasswordRequest {
  @ApiProperty({
    format: 'email',
    maxLength: 254,
    example: 'john.doe@example.com',
  })
  @IsEmail()
  @MaxLength(254)
  email: string;
}

export class ResetPasswordRequest {
  @ApiProperty({
    minLength: 1,
    description: 'The token from the reset link',
    example: 'Zq8-1rEXAMPLEp4XcT0mWb7LqN2yHs5aVd3uKf9',
  })
  @IsString()
  @MinLength(1)
  token: string;

  @ApiProperty({
    format: 'password',
    minLength: 12,
    maxLength: 20,
    example: 'Something123@',
  })
  @IsString()
  @MinLength(12)
  @MaxLength(20)
  newPassword: string;
}

export class RefreshTokenRequest {
  @ApiProperty({ minLength: 1, example: '3q2-7wEXAMPLEc0ZqXk9rT1mVb8LwN4yHs6' })
  @IsString()
  @MinLength(1)
  refreshToken: string;
}

export class RegisterResponse {
  @ApiProperty({ example: 'john_doe1' })
  username: string;
}

export class TokenResponse {
  @ApiProperty({
    description: 'JWT, HS256, claims exactly sub, roles, iat, exp',
  })
  accessToken: string;

  @ApiProperty({ description: 'Opaque, single use, valid for 7 days' })
  refreshToken: string;

  @ApiProperty({ enum: ['Bearer'] })
  tokenType: 'Bearer';

  @ApiProperty({
    enum: [900],
    description: 'Seconds until the access token expires',
  })
  expiresIn: number;
}

/** Every role a user can hold; must match the CHECK on user_roles.role (V013). */
export const ROLES = ['TRADER', 'ADMIN'] as const;

/** A user as admins see them. Never includes the password hash. */
export class AdminUser {
  @ApiProperty({ example: 'john_doe1' })
  username: string;

  @ApiProperty({
    type: 'string',
    nullable: true,
    example: 'john.doe@example.com',
    description: 'Null for accounts registered before emails were collected',
  })
  email: string | null;

  @ApiProperty({ enum: ROLES, isArray: true, example: ['TRADER'] })
  roles: string[];

  @ApiProperty({ format: 'date-time', example: '2026-10-19T09:00:00.000Z' })
  createdAt: string;

  @ApiProperty({
    type: 'string',
    nullable: true,
    example: 'test_admin',
    description: 'The admin who created the account; null if self-registered',
  })
  createdBy: string | null;
}

/** Nest's default error body; documents responses only. */
export class ErrorResponse {
  @ApiProperty()
  statusCode: number;

  @ApiProperty()
  error: string;

  @ApiProperty({
    oneOf: [{ type: 'string' }, { type: 'array', items: { type: 'string' } }],
  })
  message: string | string[];
}
