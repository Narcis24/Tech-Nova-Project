import { Body, Controller, HttpCode, Post } from '@nestjs/common';
import {
  ApiAcceptedResponse,
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
  ForgotPasswordRequest,
  RefreshTokenRequest,
  RegisterRequest,
  RegisterResponse,
  ResetPasswordRequest,
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
    description:
      'Username or email already registered; the body does not say which',
    type: ErrorResponse,
  })
  register(@Body() body: RegisterRequest): Promise<RegisterResponse> {
    return this.authService.register(body);
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

  @Post('forgot-password')
  @HttpCode(202)
  @ApiOperation({
    operationId: 'forgotPassword',
    summary: 'Email a password reset link',
    description:
      'Always 202 for a well-formed email, whether or not an account uses it. If one does, a reset link is emailed to it; an earlier unused link for that user stops working.',
  })
  @ApiAcceptedResponse({
    description:
      'Request accepted (a link was sent only if an account uses the email)',
  })
  @ApiBadRequestResponse(VALIDATION_FAILED)
  forgotPassword(@Body() body: ForgotPasswordRequest): void {
    // Not awaited: answering before the lookup and the email keeps the response time the same
    // whether or not an account uses the email. forgotPassword never throws.
    void this.authService.forgotPassword(body.email);
  }

  @Post('reset-password')
  @HttpCode(204)
  @ApiOperation({
    operationId: 'resetPassword',
    summary: 'Set a new password with the token from a reset link',
  })
  @ApiNoContentResponse({
    description: 'Password changed; every refresh token of the user is revoked',
  })
  @ApiBadRequestResponse({
    description:
      'Reset token unknown, expired or already used, or the new password breaks a field rule',
    type: ErrorResponse,
  })
  resetPassword(@Body() body: ResetPasswordRequest): Promise<void> {
    return this.authService.resetPassword(body.token, body.newPassword);
  }
}
