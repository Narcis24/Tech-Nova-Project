import { TestBed } from '@angular/core/testing';
import { BalanceCardComponent } from './balance-card.component';
describe('BalanceCardComponent', () => {
  it('compares actual stored balances and handles a zero previous balance without dividing by zero', () => {
    const fixture=TestBed.createComponent(BalanceCardComponent);
    fixture.componentRef.setInput('portfolio',{totalPortfolioValue:120,cashBalance:20});
    fixture.componentRef.setInput('history',{portfolioValue:100,cashBalance:10,sessionDate:'2026-10-14'}); fixture.detectChanges();
    expect(fixture.componentInstance.change()).toBe(20); expect(fixture.componentInstance.percentage()).toBe(0.2);
    expect(fixture.nativeElement.textContent).toContain('+20.00%');
    fixture.componentRef.setInput('history',{portfolioValue:0}); fixture.detectChanges();
    expect(fixture.componentInstance.percentage()).toBeNull(); expect(fixture.nativeElement.textContent).not.toContain('Infinity');
  });
  it('does not invent history for a new account', () => {
    const fixture=TestBed.createComponent(BalanceCardComponent); fixture.componentRef.setInput('portfolio',{totalPortfolioValue:0,cashBalance:0}); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('unavailable'); expect(fixture.componentInstance.change()).toBeNull();
  });
});
