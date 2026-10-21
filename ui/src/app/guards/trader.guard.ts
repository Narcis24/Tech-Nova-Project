import { inject } from '@angular/core';
import { CanActivateChildFn, Router } from '@angular/router';

import { AuthService } from '../services/auth.service';

/**
 * Keeps admins on the admin pages: the trading pages (portfolio, trade, orders, accounts, risk limits)
 * send them to /admin. The mirror of adminGuard, and like it only decides what the UI shows.
 * Sits under authGuard, which has already sent logged-out users to /login.
 */
export const traderGuard: CanActivateChildFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.isAdmin() ? router.createUrlTree(['/admin']) : true;
};
