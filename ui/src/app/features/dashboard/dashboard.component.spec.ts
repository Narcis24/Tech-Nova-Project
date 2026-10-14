import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Account, AccountService } from '../../services/account.service';
import { DashboardComponent } from './dashboard.component';

const ACCOUNTS = [
  { accountId: 'ACC001', holderName: 'Trader One' },
  { accountId: 'ACC002', holderName: 'Trader Two' },
] as Account[];

describe('DashboardComponent', () => {
  const accounts = signal<Account[]>(ACCOUNTS);
  const currentAccountId = signal<string | null>('ACC001');
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;

  beforeEach(async () => {
    accounts.set(ACCOUNTS);
    currentAccountId.set('ACC001');

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [{ provide: AccountService, useValue: { accounts, currentAccountId } }],
    })
    .compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function text(): string {
    return fixture.nativeElement.textContent;
  }

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  function heading(): string {
    return fixture.nativeElement.querySelector('h1').textContent.trim();
  }

  it("uses the selected account's name as the heading", () => {
    expect(heading()).toBe('Trader One');
  });

  it('updates the heading when another account is selected', () => {
    currentAccountId.set('ACC002');
    fixture.detectChanges();

    expect(heading()).toBe('Trader Two');
  });

  it('falls back to "Dashboard" when no account is selected', () => {
    currentAccountId.set(null);
    fixture.detectChanges();

    expect(heading()).toBe('Dashboard');
  });

  it('shows the placeholder', () => {
    expect(text()).toContain('Placeholder');
  });
});
