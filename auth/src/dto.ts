import { ApiProperty } from '@nestjs/swagger';
import { IsString, Matches, MaxLength, MinLength } from 'class-validator';

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
  @Matches(/^[A-Za-z0-9_]+$/)
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
