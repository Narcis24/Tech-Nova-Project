import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';
import { AccountPieControllerService, RebalancePreviewResponse } from '../../generated/trading-api-client';
import { RebalancePreviewComponent } from './rebalance-preview.component';

const PREVIEW: RebalancePreviewResponse = {
  totalValue: 10000,
  cashBefore: 10000,
  cashAfter: 2000,
  cashTargetPct: 20,
  legs: [
    {
      symbol: 'AAPL',
      inPie: true,
      currentPct: 0,
      targetPct: 50,
      currentValue: 0,
      targetValue: 5000,
      difference: 5000,
      action: 'BUY',
      quantity: 50,
      estimatedValue: 5000,
    },
    { symbol: 'TSLA', inPie: false, currentPct: 10, targetPct: 0, action: 'SELL', quantity: 5, estimatedValue: 1000 },
    { symbol: 'MSFT', inPie: true, currentPct: 30, targetPct: 30, action: 'NONE', quantity: 0, estimatedValue: 0 },
  ],
  warnings: [{ message: 'After rebalancing, AAPL exceeds your limit.' }],
};

describe('RebalancePreviewComponent', () => {
  let api: jasmine.SpyObj<{ previewRebalance: (id: string) => Observable<RebalancePreviewResponse> }>;

  beforeEach(() => {
    api = jasmine.createSpyObj('accountPies', ['previewRebalance']);
    api.previewRebalance.and.returnValue(of(PREVIEW));
    TestBed.configureTestingModule({
      imports: [RebalancePreviewComponent],
      providers: [
        provideRouter([]),
        { provide: AccountPieControllerService, useValue: api },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'acc1' } } } },
      ],
    });
  });

  function render(): HTMLElement {
    const fixture = TestBed.createComponent(RebalancePreviewComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('loads the route account and renders totals and trade actions', () => {
    const root = render();
    expect(api.previewRebalance).toHaveBeenCalledOnceWith('acc1');
    expect(root.querySelector('.summary')?.textContent).toContain('$10,000.00');
    expect(root.querySelector('.summary')?.textContent).toContain('$2,000.00');
    expect(root.querySelector('.summary')?.textContent).toContain('20%');
    expect(root.querySelector('.buy')?.textContent).toBe('BUY 50');
    expect(root.querySelector('.sell')?.textContent).toBe('SELL 5');
    expect(root.querySelectorAll('.action')[2].textContent).toBe('—');
  });

  it("shows each symbol's value now, its target value and the difference", () => {
    const row = render().querySelector('tbody tr')!;
    expect(row.textContent).toContain('$0.00 → $5,000.00');
    expect(row.querySelector('.difference')?.textContent).toBe('+$5,000.00');
  });

  it('says when no trades are needed', () => {
    api.previewRebalance.and.returnValue(
      of({ ...PREVIEW, legs: PREVIEW.legs!.map((leg) => ({ ...leg, action: 'NONE', quantity: 0 })) }),
    );
    expect(render().textContent).toContain('No trades needed');
  });

  it('does not say no trades are needed when some are', () => {
    expect(render().textContent).not.toContain('No trades needed');
  });

  it('tags holdings outside the pie', () => {
    const root = render();
    expect(root.querySelectorAll('.outside').length).toBe(1);
    expect(root.querySelector('.outside')?.closest('tr')?.textContent).toContain('TSLA');
    expect(root.querySelector('.outside')?.textContent).toContain('Not in pie: sold');
  });

  it('shows risk warnings', () => {
    expect(render().querySelector('.warnings')?.textContent).toContain(PREVIEW.warnings![0].message!);
  });

  it('links to pies when no pie is linked', () => {
    api.previewRebalance.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 404,
            error: { errorCode: 'PIE_NOT_LINKED' },
          }),
      ),
    );
    const root = render();
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('No pie is linked');
    expect(root.querySelector('a')?.getAttribute('href')).toBe('/pies');
    expect(root.querySelector('table')).toBeNull();
  });

  it('shows the missing-price message', () => {
    api.previewRebalance.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 404,
            error: { errorCode: 'PRICE_NOT_FOUND', message: 'No price for AAPL' },
          }),
      ),
    );
    expect(render().querySelector('[role="alert"]')?.textContent).toBe('No price for AAPL');
  });

  it('shows loading while the request is pending', () => {
    api.previewRebalance.and.returnValue(new Subject<RebalancePreviewResponse>());
    expect(render().querySelector('output')?.textContent).toContain('Loading');
  });

  it('shows a useful error for other failures', () => {
    api.previewRebalance.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    expect(render().querySelector('[role="alert"]')?.textContent).toContain("Couldn't load");
  });
});
