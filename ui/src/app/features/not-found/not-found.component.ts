import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink],
  template: `
    <h1>Page not found</h1>
    <a routerLink="/dashboard">Go to dashboard</a>
  `,
  styles: ``
})
export class NotFoundComponent {

}
