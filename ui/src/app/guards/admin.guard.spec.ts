import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { adminGuard } from './admin.guard';

describe('adminGuard', () => {
  const isAdmin = signal(false);

  beforeEach(() => {
    isAdmin.set(false);
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { isAdmin } }],
    });
  });

  function runGuard() {
    return TestBed.runInInjectionContext(() => adminGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
  }

  it('lets an admin through', () => {
    isAdmin.set(true);

    expect(runGuard()).toBeTrue();
  });

  it('sends anyone else to /dashboard', () => {
    const result = runGuard();

    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/dashboard');
  });

  it('reads the role at call time, so a refreshed token is seen straight away', () => {
    expect(runGuard()).toBeInstanceOf(UrlTree);

    isAdmin.set(true);

    expect(runGuard()).toBeTrue();
  });
});
