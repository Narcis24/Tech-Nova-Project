import { TestBed } from '@angular/core/testing';
import { BalanceCardComponent } from './balance-card.component';

describe('BalanceCardComponent', () => {
  function setup() {
    const fixture = TestBed.createComponent(BalanceCardComponent);
    fixture.componentRef.setInput('portfolio', { totalPortfolioValue: 120, cashBalance: 20 });
    return fixture;
  }

  it('shows the previous close value and the change since then', () => {
    const fixture = setup();
    fixture.componentRef.setInput('dayChange', 20);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('+$20.00');
    expect(text).toContain('+20.00%');
    expect(text).toContain('Previous close value: $100.00');
    expect(fixture.nativeElement.querySelector('.change').classList).toContain('gain');
  });

  it('shows a loss, and no percentage when the previous value is not positive', () => {
    const fixture = setup();
    fixture.componentRef.setInput('dayChange', -5);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.change').classList).toContain('loss');
    expect(fixture.nativeElement.textContent).toContain('(-4.00%)');
    fixture.componentRef.setInput('dayChange', 120);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.change').textContent).not.toContain('%');
  });

  it('shows the cash free to trade, and what pending orders lock, only when they lock some', () => {
    const fixture = setup();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.cash strong').textContent).toContain('$20.00');
    expect(fixture.nativeElement.querySelector('.cash .locked')).toBeNull();
    fixture.componentRef.setInput('portfolio', {
      totalPortfolioValue: 120,
      cashBalance: 20,
      lockedCash: 15,
      availableCash: 5,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.cash strong').textContent).toContain('$5.00');
    expect(fixture.nativeElement.querySelector('.cash .locked').textContent).toContain(
      '+ $15.00 locked in pending orders',
    );
  });

  it('hides the change line when it is unavailable', () => {
    const fixture = setup();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.change')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('$120.00');
  });
});
