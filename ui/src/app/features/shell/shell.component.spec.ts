import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AccountService } from '../../services/account.service';
import { AuthService } from '../../services/auth.service';
import { ShellComponent } from './shell.component';

describe('ShellComponent', () => {
  let component: ShellComponent;
  let fixture: ComponentFixture<ShellComponent>;
  let loadAccounts: jasmine.Spy;
  const isAdmin = signal(false);

  async function create(admin: boolean): Promise<void> {
    isAdmin.set(admin);
    loadAccounts = jasmine.createSpy('loadAccounts').and.returnValue(of(undefined));
    await TestBed.configureTestingModule({
      imports: [ShellComponent],
      // The shell loads the accounts and its nav bar reads the username; fakes avoid needing HttpClient
      providers: [
        provideRouter([]),
        // The nav bar shows the username, and admin or trader tabs by isAdmin
        {
          provide: AuthService,
          useValue: { username: signal(null), isAdmin, homeUrl: computed(() => (isAdmin() ? '/admin' : '/dashboard')) },
        },
        {
          provide: AccountService,
          useValue: { accounts: signal([]), currentAccountId: signal(null), loadAccounts },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ShellComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => create(false));

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders a header above the routed page', () => {
    const page: HTMLElement = fixture.nativeElement;

    expect(page.querySelector('header')).not.toBeNull();
    expect(page.querySelector('main router-outlet')).not.toBeNull();
  });

  it("loads a trader's accounts", () => {
    expect(loadAccounts).toHaveBeenCalledTimes(1);
  });

  it('does not load accounts for an admin, who has none', async () => {
    TestBed.resetTestingModule();
    await create(true);

    expect(loadAccounts).not.toHaveBeenCalled();
  });
});
