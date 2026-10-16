import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Route, Router, UrlSegment, UrlTree, provideRouter } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  const isAuthenticated = signal(false);

  beforeEach(() => {
    isAuthenticated.set(false);
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { isAuthenticated } }],
    });
  });

  function runGuard() {
    return TestBed.runInInjectionContext(() => authGuard({} as Route, [] as UrlSegment[]));
  }

  it('lets an authenticated user through', () => {
    isAuthenticated.set(true);

    expect(runGuard()).toBeTrue();
  });

  it('redirects an unauthenticated user to /login with a returnUrl', () => {
    const result = runGuard();

    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toMatch(/^\/login\?returnUrl=/);
  });

  it('reads the session at call time, so a restored session is seen straight away', () => {
    expect(runGuard()).toBeInstanceOf(UrlTree);

    isAuthenticated.set(true);

    expect(runGuard()).toBeTrue();
  });
});
