import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AccountService } from '../../services/account.service';
import { AuthService } from '../../services/auth.service';
import { ShellComponent } from './shell.component';

describe('ShellComponent', () => {
  let component: ShellComponent;
  let fixture: ComponentFixture<ShellComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShellComponent],
      // The shell loads the accounts and its nav bar reads the username; fakes avoid needing HttpClient
      providers: [
        provideRouter([]),
        // The nav bar shows the username, and an Admin tab when isAdmin
        { provide: AuthService, useValue: { username: signal(null), isAdmin: signal(false) } },
        {
          provide: AccountService,
          useValue: { accounts: signal([]), currentAccountId: signal(null), loadAccounts: () => of(undefined) },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ShellComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders a header above the routed page', () => {
    const page: HTMLElement = fixture.nativeElement;

    expect(page.querySelector('header')).not.toBeNull();
    expect(page.querySelector('main router-outlet')).not.toBeNull();
  });
});
