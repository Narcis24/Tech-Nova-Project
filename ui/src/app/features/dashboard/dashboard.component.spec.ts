import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AccountControllerService, DashboardControllerService, InstrumentControllerService, PortfolioControllerService } from '../../generated/trading-api-client';
import { Account, AccountService } from '../../services/account.service';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent', () => {
  const currentAccountId = signal<string | null>('ACC001');
  let fixture: ComponentFixture<DashboardComponent>;
  beforeEach(async () => {
    currentAccountId.set('ACC001');
    await TestBed.configureTestingModule({ imports: [DashboardComponent], providers: [provideRouter([]),
      { provide: DashboardControllerService, useValue: { getBalanceHistory: () => of({}), getMarketOverview: () => of([]) } },
      { provide: AccountService, useValue: { currentAccountId, accounts: signal([{ accountId: 'ACC001', holderName: 'Trader' }] as Account[]) } },
      { provide: PortfolioControllerService, useValue: { getPortfolioMetrics: () => of({ totalPortfolioValue: 120, cashBalance: 10, totalUnrealizedPnL: 10, positions: [{ symbol: 'SPY', quantity: 2, averageCost: 50, currentPrice: 55, marketValue: 110, unrealizedPnL: 10 }] }) } },
      { provide: InstrumentControllerService, useValue: { getInstruments: () => of([{ symbol: 'SPY', name: 'SPDR S&P 500 ETF', assetClass: 'ETF' }]) } },
      { provide: AccountControllerService, useValue: { getAccountOrders: () => of([]) } },
    ] }).compileComponents();
    fixture = TestBed.createComponent(DashboardComponent); fixture.detectChanges(); fixture.detectChanges();
  });
  it('renders portfolio values and instrument details', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('$120.00'); expect(text).toContain('SPDR S&P 500 ETF');
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
    expect(text).not.toContain('NaN');
  });
  it('provides an account action when no account is selected', () => {
    currentAccountId.set(null); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Manage accounts');
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });
});
