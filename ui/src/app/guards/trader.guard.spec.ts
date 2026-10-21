import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { traderGuard } from './trader.guard';

describe('traderGuard', () => {
  const isAdmin = signal(false);

  beforeEach(() => {
    isAdmin.set(false);
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { isAdmin } }],
    });
  });

  function runGuard() {
    return TestBed.runInInjectionContext(() => traderGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
  }

  it('lets a trader through', () => {
    expect(runGuard()).toBeTrue();
  });

  it('sends an admin to /admin', () => {
    isAdmin.set(true);

    const result = runGuard();

    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/admin');
  });

  it('reads the role at call time, so a refreshed token is seen straight away', () => {
    expect(runGuard()).toBeTrue();

    isAdmin.set(true);

    expect(runGuard()).toBeInstanceOf(UrlTree);
  });
});
