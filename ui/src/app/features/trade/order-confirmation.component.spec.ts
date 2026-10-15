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
  const confirmButton = (root: HTMLElement) => root.querySelector<HTMLButtonElement>('.actions button:not(.secondary)')!;

  it('looks as before when there are no warnings', () => {
    const root = setup();
    expect(root.querySelector('.warnings')).toBeNull();
    expect(confirmButton(root).textContent).toContain('Confirm order');
  });

  it('lists every warning with its figures and offers Place anyway', () => {
    const root = setup(warnings);
    const items = root.querySelectorAll('.warnings li');
    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('AAPL would be 35% of your portfolio.');
    expect(items[0].textContent).toContain('35% after this order · limit 20%');
    expect(items[1].textContent).toContain('12.5% after this order · your limit 20%');
    expect(confirmButton(root).textContent).toContain('Place anyway');
  });

  it('links to the risk limits page from the warnings', () => {
    const root = setup(warnings);
    expect(root.querySelector('.warnings a')?.getAttribute('href')).toBe('/risk-limits');
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
