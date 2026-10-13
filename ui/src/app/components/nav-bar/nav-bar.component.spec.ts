import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthService } from '../../services/auth.service';
import { NavBarComponent } from './nav-bar.component';

// An empty page for the tab links to point at
@Component({ template: '' })
class BlankComponent {}

describe('NavBarComponent', () => {
  const username = signal<string | null>('trader01');
  let auth: { username: typeof username; logout: jasmine.Spy };
  let component: NavBarComponent;
  let fixture: ComponentFixture<NavBarComponent>;

  beforeEach(async () => {
    username.set('trader01');
    auth = { username, logout: jasmine.createSpy('logout') };

    await TestBed.configureTestingModule({
      imports: [NavBarComponent],
      providers: [
        provideRouter([
          { path: 'dashboard', component: BlankComponent },
          { path: 'trade', component: BlankComponent },
          { path: 'orders', component: BlankComponent },
        ]),
        { provide: AuthService, useValue: auth },
      ],
    })
    .compileComponents();

    fixture = TestBed.createComponent(NavBarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('marks only the current tab with aria-current', async () => {
    await TestBed.inject(Router).navigateByUrl('/trade');
    fixture.detectChanges();

    const current: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('[aria-current="page"]'));
    expect(current.map(tab => tab.textContent?.trim())).toEqual(['Trade']);
  });

  it("shows the user's initial and name", () => {
    const text: string = fixture.nativeElement.querySelector('.user').textContent;

    expect(text).toContain('T');
    expect(text).toContain('trader01');
  });

  it('hides the user badge when logged out', () => {
    username.set(null);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.user')).toBeNull();
  });

  it('signs out when Sign out is clicked', () => {
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.sign-out');
    button.click();

    expect(auth.logout).toHaveBeenCalled();
  });
});
