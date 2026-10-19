import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../services/auth.service';

/**
 * Lets admins through and sends everyone else to /dashboard. Only decides what the UI shows: the admin
 * API checks the token's signature and refuses non-admins (403) whatever the browser does.
 * Sits under authGuard, which has already sent logged-out users to /login.
 */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.isAdmin() ? true : router.createUrlTree(['/dashboard']);
};
