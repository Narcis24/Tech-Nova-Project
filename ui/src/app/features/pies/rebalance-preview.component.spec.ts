import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';
import {
  AccountPieControllerService,
  RebalanceControllerService,
  RebalancePreviewResponse,
} from '../../generated/trading-api-client';
import { RebalancePreviewComponent } from './rebalance-preview.component';

const PREVIEW: RebalancePreviewResponse = {
  totalValue: 10000,
  cashBefore: 10000,
  cashAfter: 2000,
  cashTargetPct: 20,
  legs: [
    { symbol: 'AAPL', inPie: true, currentPct: 0, targetPct: 50, action: 'BUY', quantity: 50, estimatedValue: 5000 },
    { symbol: 'TSLA', inPie: false, currentPct: 10, targetPct: 0, action: 'SELL', quantity: 5, estimatedValue: 1000 },
    { symbol: 'MSFT', inPie: true, currentPct: 30, targetPct: 30, action: 'NONE', quantity: 0, estimatedValue: 0 },
  ],
  warnings: [{ message: 'After rebalancing, AAPL exceeds your limit.' }],
};

describe('RebalancePreviewComponent', () => {
  let api: jasmine.SpyObj<{
    previewRebalance: (id: string) => Observable<RebalancePreviewResponse>;
    previewCopy: (id: string, body: { pieId: string }) => Observable<RebalancePreviewResponse>;
    copyPie: (id: string, body: { pieId: string }) => Observable<{ runId: string }>;
  }>;
  let start: jasmine.Spy;
  let copyParam: string | null;

  beforeEach(() => {
    copyParam = null;
    api = jasmine.createSpyObj('accountPies', ['previewRebalance', 'previewCopy', 'copyPie']);
    api.previewRebalance.and.returnValue(of(PREVIEW));
    api.previewCopy.and.returnValue(of(PREVIEW));
    api.copyPie.and.returnValue(of({ runId: 'run2' }));
    start = jasmine.createSpy('startRebalance').and.returnValue(of({ runId: 'run1' }));
    TestBed.configureTestingModule({
      imports: [RebalancePreviewComponent],
      providers: [
        provideRouter([]),
        { provide: AccountPieControllerService, useValue: api },
        { provide: RebalanceControllerService, useValue: { startRebalance: start } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'acc1' }, queryParamMap: { get: () => copyParam } } },
        },
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

  it('places nothing until confirmed, then starts once and opens the run', () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const root = render();
    expect(start).not.toHaveBeenCalled();
    root.querySelector<HTMLButtonElement>('button')!.click();
    root.querySelector<HTMLButtonElement>('button')!.click();
    expect(start).toHaveBeenCalledOnceWith('acc1');
    expect(navigate).toHaveBeenCalledWith(['/rebalance-runs', 'run1']);
  });

  it('shows a start conflict and allows retrying', () => {
    start.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'An active run already exists' } })),
    );
    const fixture = TestBed.createComponent(RebalancePreviewComponent);
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    root.querySelector<HTMLButtonElement>('button')!.click();
    fixture.detectChanges();
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('An active run already exists');
    expect(root.querySelector<HTMLButtonElement>('button')!.disabled).toBeFalse();
  });

  describe('copying a public pie', () => {
    beforeEach(() => (copyParam = 'p9'));

    it("previews the copy rather than the account's own pie", () => {
      const root = render();
      expect(api.previewCopy).toHaveBeenCalledOnceWith('acc1', { pieId: 'p9' });
      expect(api.previewRebalance).not.toHaveBeenCalled();
      expect(root.textContent).toContain('auto-sync on');
      expect(root.querySelector('a')?.textContent).toContain('Back to pie');
    });

    it('confirms by copying and opens the run', () => {
      const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
      const root = render();
      const confirm = Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.includes('Confirm copy'))!;
      confirm.click();
      expect(api.copyPie).toHaveBeenCalledOnceWith('acc1', { pieId: 'p9' });
      expect(start).not.toHaveBeenCalled();
      expect(navigate).toHaveBeenCalledWith(['/rebalance-runs', 'run2']);
    });

    it("explains why a pie can't be copied", () => {
      api.previewCopy.and.returnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 409,
              error: {
                errorCode: 'REBALANCE_CONFLICT',
                message: "You can't copy your own pie. Link it to the account instead",
              },
            }),
        ),
      );
      expect(render().querySelector('[role="alert"]')?.textContent).toContain("You can't copy your own pie");
    });

    it('says when the pie is no longer public', () => {
      api.previewCopy.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 404, error: { errorCode: 'PIE_NOT_FOUND' } })),
      );
      expect(render().querySelector('[role="alert"]')?.textContent).toContain("isn't public any more");
    });
  });
});
