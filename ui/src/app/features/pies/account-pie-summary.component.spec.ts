import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { AccountPieControllerService, AccountPieResponse } from '../../generated/trading-api-client';
import { AccountPieSummaryComponent } from './account-pie-summary.component';

describe('AccountPieSummaryComponent', () => {
  let get: jasmine.Spy;
  beforeEach(() => {
    get = jasmine.createSpy('getAccountPie').and.returnValue(of({ pieName: 'Growth', maxDriftPct: 5 }));
    TestBed.configureTestingModule({
      imports: [AccountPieSummaryComponent],
      providers: [provideRouter([]), { provide: AccountPieControllerService, useValue: { getAccountPie: get } }],
    });
  });
  it('offers a rebalance at five points of drift', () => {
    const fixture = TestBed.createComponent(AccountPieSummaryComponent);
    fixture.componentRef.setInput('accountId', 'acc1');
    fixture.detectChanges();
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.textContent).toContain('Growth');
    expect(root.textContent).toContain('Drift 5%');
    expect(root.querySelector('a')?.textContent).toBe('Rebalance');
    expect(root.querySelector('a')?.getAttribute('href')).toBe('/accounts/acc1/rebalance');
  });
  it('clears an old account and ignores its late response after switching accounts', () => {
    const old = new Subject<AccountPieResponse>();
    get.and.returnValues(old, of({ pieName: 'New account pie', maxDriftPct: 1 }));
    const fixture = TestBed.createComponent(AccountPieSummaryComponent);
    fixture.componentRef.setInput('accountId', 'acc1');
    fixture.detectChanges();
    fixture.componentRef.setInput('accountId', 'acc2');
    fixture.detectChanges();
    old.next({ pieName: 'Old account pie', maxDriftPct: 50 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('New account pie');
    expect(fixture.nativeElement.textContent).not.toContain('Old account pie');
  });
  it('hides the summary for an unlinked account', () => {
    get.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 404, error: { errorCode: 'PIE_NOT_LINKED' } })),
    );
    const fixture = TestBed.createComponent(AccountPieSummaryComponent);
    fixture.componentRef.setInput('accountId', 'acc1');
    fixture.detectChanges();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.pie-summary')).toBeNull();
    expect(fixture.nativeElement.querySelector('.error')).toBeNull();
  });
  it('keeps an unpriced linked pie visible and opens its active run', () => {
    get.and.returnValue(of({ pieName: 'Growth', maxDriftPct: null, activeRunId: 'run1' }));
    const fixture = TestBed.createComponent(AccountPieSummaryComponent);
    fixture.componentRef.setInput('accountId', 'acc1');
    fixture.detectChanges();
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.textContent).toContain('Growth');
    expect(root.textContent).not.toContain('Drift');
    expect(root.querySelector('a')?.getAttribute('href')).toBe('/rebalance-runs/run1');
  });
});
