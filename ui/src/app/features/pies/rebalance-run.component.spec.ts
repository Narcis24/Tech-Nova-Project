import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { RebalanceControllerService } from '../../generated/trading-api-client';
import { RebalanceRunComponent } from './rebalance-run.component';

describe('RebalanceRunComponent', () => {
  let get: jasmine.Spy;
  let cancel: jasmine.Spy;
  let fixture: ComponentFixture<RebalanceRunComponent>;
  const RUN = {
    runId: 'run1',
    accountId: 'acc1',
    targetVersion: 2,
    status: 'SELLING',
    legs: [{ orderId: 'order1', side: 'SELL', symbol: 'AAPL', quantity: 2, status: 'PENDING' }],
  };
  beforeEach(() => {
    get = jasmine.createSpy('getRebalanceRun').and.returnValue(of(RUN));
    cancel = jasmine.createSpy('cancelRebalanceRun').and.returnValue(of({ ...RUN, status: 'CANCELLED' }));
    TestBed.configureTestingModule({
      imports: [RebalanceRunComponent],
      providers: [
        provideRouter([]),
        { provide: RebalanceControllerService, useValue: { getRebalanceRun: get, cancelRebalanceRun: cancel } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'run1' } } } },
      ],
    });
  });
  function create(): HTMLElement {
    fixture = TestBed.createComponent(RebalanceRunComponent);
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
    return fixture.nativeElement;
  }
  afterEach(() => fixture?.destroy());

  it('polls active runs and stops once complete', fakeAsync(() => {
    const root = create();
    expect(get).toHaveBeenCalledOnceWith('run1');
    expect(root.querySelector('.legs')?.textContent).toContain('SELL 2 AAPL');
    get.and.returnValue(of({ ...RUN, status: 'COMPLETED' }));
    tick(2000);
    fixture.detectChanges();
    expect(root.querySelector('h2')?.textContent).toBe('Rebalance complete');
    tick(6000);
    expect(get).toHaveBeenCalledTimes(2);
  }));
  it('shows market closed and cancels pending orders', fakeAsync(() => {
    get.and.returnValue(of({ ...RUN, waitingForMarketOpen: true }));
    const root = create();
    expect(root.textContent).toContain('Waiting for market open');
    get.and.returnValue(of({ ...RUN, status: 'CANCELLED' }));
    root.querySelector<HTMLButtonElement>('.actions button')!.click();
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
    expect(cancel).toHaveBeenCalledOnceWith('run1');
    expect(root.querySelector('h2')?.textContent).toBe('Rebalance cancelled');
    expect(root.querySelector('.actions button')).toBeNull();
  }));
  it('shows partial failures and order links', fakeAsync(() => {
    get.and.returnValue(
      of({ ...RUN, status: 'PARTIAL', legs: [{ ...RUN.legs[0], status: 'REJECTED', failureReason: 'No quote' }] }),
    );
    const root = create();
    expect(root.querySelector('h2')?.textContent).toBe('Partly done');
    expect(root.querySelector('.error')?.textContent).toContain('No quote');
    expect(root.querySelector('.legs a')?.getAttribute('href')).toBe('/orders/order1');
  }));
  it('keeps the last status on a polling failure and retries', fakeAsync(() => {
    const root = create();
    get.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    tick(2000);
    fixture.detectChanges();
    expect(root.querySelector('[role="alert"]')?.textContent).toContain("Couldn't refresh");
    expect(root.querySelector('.legs')).not.toBeNull();
    get.and.returnValue(of({ ...RUN, status: 'COMPLETED' }));
    root.querySelector<HTMLButtonElement>('.feedback button')!.click();
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
    expect(root.querySelector('h2')?.textContent).toBe('Rebalance complete');
  }));
  it('does not leave polling running after navigating away', fakeAsync(() => {
    create();
    fixture.destroy();
    tick(6000);
    expect(get).toHaveBeenCalledTimes(1);
  }));
});
