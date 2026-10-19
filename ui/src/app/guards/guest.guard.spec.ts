import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { guestGuard } from './guest.guard';

describe('guestGuard', () => {
  const isAuthenticated = signal(false);
  const homeUrl = signal('/dashboard');

  beforeEach(() => {
    isAuthenticated.set(false);
    homeUrl.set('/dashboard');
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { isAuthenticated, homeUrl } }],
    });
  });

  function runGuard() {
    return TestBed.runInInjectionContext(() => guestGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
  }

  it('lets an unauthenticated user through', () => {
    expect(runGuard()).toBeTrue();
  });

  it('redirects an authenticated user to /dashboard', () => {
    isAuthenticated.set(true);

    const result = runGuard();

    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/dashboard');
  });

  it('redirects an authenticated admin to /admin', () => {
    isAuthenticated.set(true);
    homeUrl.set('/admin');

    const result = runGuard();

    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/admin');
  });
});
