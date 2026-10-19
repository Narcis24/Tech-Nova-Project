import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { PlaceOrderRequest, RiskWarning } from '../../generated/trading-api-client';
import { OrderConfirmationComponent } from './order-confirmation.component';

describe('OrderConfirmationComponent', () => {
  const order: PlaceOrderRequest = {
    accountId: 'ACC001',
    symbol: 'AAPL',
    side: 'BUY',
    orderType: 'MARKET',
    quantity: 25,
    idempotencyKey: 'key',
  };
  const warnings: RiskWarning[] = [
    {
      rule: 'SINGLE_INSTRUMENT',
      subject: 'AAPL',
      resultingPct: 35,
      limitPct: 20,
      limitSource: 'DEFAULT',
      message: 'AAPL would be 35% of your portfolio.',
    },
    {
      rule: 'CASH_FLOOR',
      subject: 'CASH',
      resultingPct: 12.5,
      limitPct: 20,
      limitSource: 'CUSTOM',
      message: 'Cash would fall to 12.5% of your portfolio.',
    },
  ];

  function setup(riskWarnings: RiskWarning[] = []) {
    TestBed.configureTestingModule({ imports: [OrderConfirmationComponent], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(OrderConfirmationComponent);
    fixture.componentRef.setInput('order', order);
    fixture.componentRef.setInput('warnings', riskWarnings);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }
  const confirmButton = (root: HTMLElement) =>
    root.querySelector<HTMLButtonElement>('.actions button:not(.secondary)')!;

  it('looks as before when there are no warnings', () => {
    const root = setup();
    expect(root.querySelector('.warnings')).toBeNull();
    expect(confirmButton(root).textContent).toContain('Confirm order');
  });

  it('lists every warning and offers Place anyway', () => {
    const root = setup(warnings);
    const items = root.querySelectorAll('.warnings li');
    expect(items.length).toBe(2);
    expect(root.querySelector('.warnings h3')?.textContent).toContain('Review your risk limits');
    expect(root.querySelector('.warnings-intro')?.textContent).toContain(
      'This order would exceed the following limits:',
    );
    expect(items[0].textContent).toContain('AAPL would be 35% of your portfolio.');
    expect(items[1].textContent).toContain('Cash would fall to 12.5% of your portfolio.');
    expect(root.querySelector('.limits-link')?.textContent).toContain('Adjust risk limits');
    expect(confirmButton(root).textContent).toContain('Place anyway');
  });

  it('links to the risk limits page from the warnings', () => {
    const root = setup(warnings);
    expect(root.querySelector('.warnings a')?.getAttribute('href')).toBe('/risk-limits');
  });

  it('says the market is closed when it is', () => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date('2026-10-16T07:24:00Z')); // Fri 03:24 in New York
    const root = setup();
    jasmine.clock().uninstall();
    expect(root.querySelector('.market-closed')?.textContent).toContain('The market is closed');
  });

  it('says nothing about hours while the market is open', () => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date('2026-10-14T15:00:00Z')); // Wed 11:00 in New York
    const root = setup();
    jasmine.clock().uninstall();
    expect(root.querySelector('.market-closed')).toBeNull();
  });

  it('hides the warnings once the order is submitted', () => {
    TestBed.configureTestingModule({ imports: [OrderConfirmationComponent], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(OrderConfirmationComponent);
    fixture.componentRef.setInput('order', order);
    fixture.componentRef.setInput('warnings', warnings);
    fixture.componentRef.setInput('result', { orderId: 'placed', status: 'PENDING' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.warnings')).toBeNull();
  });
});
