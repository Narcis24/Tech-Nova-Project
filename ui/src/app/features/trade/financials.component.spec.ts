import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { FundamentalsResponse, InstrumentControllerService } from '../../generated/trading-api-client';
import { FinancialsComponent } from './financials.component';

describe('FinancialsComponent', () => {
  const apple: FundamentalsResponse = {
    symbol: 'AAPL',
    quoteType: 'EQUITY',
    sector: 'Technology',
    industry: 'Consumer Electronics',
    description: 'Apple designs, manufactures and markets smartphones.',
    website: 'https://www.apple.com',
    employees: 150000,
    marketCap: 5081985449984,
    trailingPe: 39.93,
    epsTtm: 8.72,
    dividendYield: 0.0031,
    profitMargin: 0.2762,
    revenueGrowth: 0.164,
    updatedAt: '2026-10-23T12:00:00Z',
    quarters: [
      { periodEnd: '2026-03-31', revenue: 111184000000, netIncome: 29578000000, epsDiluted: 2.01 },
      { periodEnd: '2026-06-30', revenue: 109417000000, netIncome: -1000000000, epsDiluted: -0.05 },
    ],
    years: [{ periodEnd: '2025-09-30', revenue: 416161000000, netIncome: 112010000000, epsDiluted: 7.46 }],
  };
  const fund: FundamentalsResponse = {
    symbol: 'SPY',
    quoteType: 'ETF',
    category: 'Large Blend',
    fundFamily: 'State Street Investment Management',
    totalAssets: 809216114688,
    expenseRatio: 0.000945,
    dividendYield: 0.0099,
    updatedAt: '2026-10-23T12:00:00Z',
    quarters: [],
    years: [],
  };

  function render(symbol: string, response: Observable<FundamentalsResponse | null>) {
    const api = jasmine.createSpyObj('instruments', ['getFundamentals']);
    api.getFundamentals.and.returnValue(response);
    TestBed.configureTestingModule({ providers: [{ provide: InstrumentControllerService, useValue: api }] });
    const fixture = TestBed.createComponent(FinancialsComponent);
    fixture.componentRef.setInput('symbol', symbol);
    fixture.detectChanges();
    return fixture;
  }
  const figure = (root: HTMLElement, label: string) =>
    Array.from(root.querySelectorAll('.figures div'))
      .find((d) => d.querySelector('dt')?.textContent?.trim() === label)
      ?.querySelector('dd')
      ?.textContent?.trim();

  it("shows a company's valuation in readable units", () => {
    const root: HTMLElement = render('AAPL', of(apple)).nativeElement;
    expect(root.querySelector('h2')?.textContent).toBe('Financials');
    expect(root.textContent).toContain('Technology · Consumer Electronics');
    expect(figure(root, 'Market cap')).toBe('$5.08T');
    expect(figure(root, 'P/E (TTM)')).toBe('39.9');
    expect(figure(root, 'EPS (TTM)')).toBe('8.72');
    expect(figure(root, 'Dividend yield')).toBe('0.31%');
    expect(figure(root, 'Revenue growth (YoY)')).toBe('+16.4%');
    expect(figure(root, 'Forward P/E')).toBe('—');
  });

  it('charts revenue and net income per quarter or year, with a loss below the zero line', () => {
    const fixture = render('AAPL', of(apple)),
      root: HTMLElement = fixture.nativeElement,
      component = fixture.componentInstance;
    expect(Array.from(root.querySelectorAll('thead th[scope="col"]')).map((th) => th.textContent?.trim())).toEqual([
      'Mar 2026',
      'Jun 2026',
    ]);
    expect(root.querySelectorAll('rect.revenue').length).toBe(2);
    const [, lossQuarter] = component.bars();
    expect(lossQuarter.income.loss).toBeTrue();
    expect(lossQuarter.income.y).toBe(component.zeroY());
    expect(root.querySelector('tbody td.loss')?.textContent?.trim()).toBe('-$1B');

    root.querySelectorAll<HTMLButtonElement>('.toggle button')[1].click();
    fixture.detectChanges();
    expect(root.querySelector('thead th[scope="col"]')?.textContent?.trim()).toBe('FY 2025');
    expect(root.querySelector('tbody tr:last-child td')?.textContent?.trim()).toBe('7.46');
  });

  it('clamps the description until asked for more', () => {
    const fixture = render('AAPL', of(apple)),
      root: HTMLElement = fixture.nativeElement,
      more = root.querySelector<HTMLButtonElement>('.about .text-button')!;
    expect(root.querySelector('#about-text')?.classList).toContain('clamped');
    more.click();
    fixture.detectChanges();
    expect(root.querySelector('#about-text')?.classList).not.toContain('clamped');
    expect(more.getAttribute('aria-expanded')).toBe('true');
  });

  it('shows fund facts and no income chart for an ETF', () => {
    const root: HTMLElement = render('SPY', of(fund)).nativeElement;
    expect(root.querySelector('h2')?.textContent).toBe('Fund facts');
    expect(root.textContent).toContain('Large Blend · State Street Investment Management');
    expect(figure(root, 'Total assets')).toBe('$809.22B');
    expect(figure(root, 'Expense ratio')).toBe('0.09%');
    expect(root.querySelector('svg')).toBeNull();
    expect(root.querySelector('.toggle')).toBeNull();
  });

  it('shows nothing for a symbol the pipeline has not loaded', () => {
    expect(render('TSLA', of(null)).nativeElement.querySelector('section')).toBeNull();
  });

  it('shows nothing when the request fails', () => {
    const failed = throwError(() => new Error('down'));
    expect(render('TSLA', failed).nativeElement.querySelector('section')).toBeNull();
  });
});
