import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

interface NavTab {
  label: string;
  path: string;
}

@Component({
  selector: 'app-nav-bar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './nav-bar.component.html',
  styleUrl: './nav-bar.component.css'
})
export class NavBarComponent {
  readonly tabs: readonly NavTab[] = [
    { label: 'Portfolio', path: '/dashboard' },
    { label: 'Trade', path: '/trade' },
    { label: 'Orders', path: '/orders' },
  ];
}
