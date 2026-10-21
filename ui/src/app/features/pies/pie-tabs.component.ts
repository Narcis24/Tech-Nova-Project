import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

/** Switches between the account holder's own pies and the public templates. */
@Component({
  selector: 'app-pie-tabs',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './pie-tabs.component.html',
  styleUrl: './pie-tabs.component.css',
})
export class PieTabsComponent {}
