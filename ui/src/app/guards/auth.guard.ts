import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, Router } from '@angular/router';

import { AuthService } from '../services/auth.service';

/**
 * Lets authenticated users through and sends everyone else to /login, keeping the attempted URL as returnUrl.
 * Needs no waiting on reload: provideAppInitializer restores the session before the first navigation.
 */
export const authGuard: CanActivateFn & CanMatchFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }

  // finalUrl has redirects applied (so / becomes /dashboard); it is only set by the time canActivate runs
  const navigation = router.getCurrentNavigation();
  const returnUrl = (navigation?.finalUrl ?? navigation?.extractedUrl)?.toString() ?? '/';
  return router.createUrlTree(['/login'], { queryParams: { returnUrl } });
};
