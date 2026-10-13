import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../../services/auth.service';
import { ShellComponent } from './shell.component';

describe('ShellComponent', () => {
  let component: ShellComponent;
  let fixture: ComponentFixture<ShellComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShellComponent],
      // The nav bar inside the shell reads the username; a fake avoids needing HttpClient
      providers: [provideRouter([]), { provide: AuthService, useValue: { username: signal(null) } }],
    })
    .compileComponents();

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
