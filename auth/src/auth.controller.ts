import { Body, Controller, HttpCode, Post } from '@nestjs/common';
import {
  ApiBadRequestResponse,
  ApiConflictResponse,
  ApiCreatedResponse,
  ApiNoContentResponse,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
  ApiUnauthorizedResponse,
} from '@nestjs/swagger';
import { AuthService } from './auth.service.js';
import {
  Credentials,
  ErrorResponse,
  RefreshTokenRequest,
  RegisterResponse,
  TokenResponse,
} from './dto.js';

const VALIDATION_FAILED = {
  description:
    'Body missing, malformed or breaking a field rule; one message per broken rule',
  type: ErrorResponse,
};

@ApiTags('auth')
@Controller('v1/auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Post('register')
  @HttpCode(201)
  @ApiOperation({ operationId: 'register', summary: 'Register a new user' })
  @ApiCreatedResponse({
    description: 'User registered',
    type: RegisterResponse,
  })
  @ApiBadRequestResponse(VALIDATION_FAILED)
  @ApiConflictResponse({
    description: 'Username already registered',
    type: ErrorResponse,
  })
  register(@Body() credentials: Credentials): Promise<RegisterResponse> {
    return this.authService.register(credentials);
  }

  @Post('login')
  @HttpCode(200)
  @ApiOperation({
    operationId: 'login',
    summary: 'Log in and receive an access token and a refresh token',
  })
  @ApiOkResponse({
    description: 'Credentials accepted, tokens issued',
    type: TokenResponse,
  })
  @ApiBadRequestResponse(VALIDATION_FAILED)
  @ApiUnauthorizedResponse({
    description:
      'Unknown username or wrong password; the body is identical in both cases',
    type: ErrorResponse,
  })
  login(@Body() credentials: Credentials): Promise<TokenResponse> {
    return this.authService.login(credentials);
  }

  @Post('refresh')
  @HttpCode(200)
  @ApiOperation({
    operationId: 'refresh',
    summary:
      'Exchange a refresh token for a new access token and a new refresh token',
    description:
      'The refresh token sent is revoked; only the one returned can be used next.',
  })
  @ApiOkResponse({ description: 'Tokens issued', type: TokenResponse })
  @ApiBadRequestResponse(VALIDATION_FAILED)
  @ApiUnauthorizedResponse({
    description: 'Refresh token unknown, expired or already used',
    type: ErrorResponse,
  })
  refresh(@Body() body: RefreshTokenRequest): Promise<TokenResponse> {
    return this.authService.refresh(body.refreshToken);
  }

  @Post('logout')
  @HttpCode(204)
  @ApiOperation({
    operationId: 'logout',
    summary: 'Revoke a refresh token',
    description:
      'Idempotent: an unknown, expired or already revoked refresh token also gets 204.',
  })
  @ApiNoContentResponse({
    description: 'Refresh token revoked (or was not valid)',
  })
  @ApiBadRequestResponse(VALIDATION_FAILED)
  logout(@Body() body: RefreshTokenRequest): Promise<void> {
    return this.authService.logout(body.refreshToken);
  }
}
