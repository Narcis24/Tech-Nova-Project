import { TestBed } from '@angular/core/testing';
import { BalanceCardComponent } from './balance-card.component';

describe('BalanceCardComponent', () => {
  function setup() {
    const fixture = TestBed.createComponent(BalanceCardComponent);
    fixture.componentRef.setInput('portfolio', { totalPortfolioValue: 120, cashBalance: 20 });
    return fixture;
  }

  it('shows the change since the previous close', () => {
    const fixture = setup();
    fixture.componentRef.setInput('dayChange', { amount: 20, percent: 0.2 });
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('+$20.00');
    expect(text).toContain('+20.00%');
    expect(fixture.nativeElement.querySelector('.change').classList).toContain('gain');
  });

  it('shows a loss without a percentage when the previous value is unknown', () => {
    const fixture = setup();
    fixture.componentRef.setInput('dayChange', { amount: -5, percent: null });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.change').classList).toContain('loss');
    expect(fixture.nativeElement.textContent).not.toContain('%');
  });

  it('hides the change line when it is unavailable', () => {
    const fixture = setup();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.change')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('$120.00');
  });
});
