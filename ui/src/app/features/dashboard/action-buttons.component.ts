import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
@Component({
  selector: 'app-action-buttons',
  imports: [RouterLink],
  templateUrl: './action-buttons.component.html',
  styleUrl: './action-buttons.component.css',
})
export class ActionButtonsComponent {
  readonly symbol = input.required<string>();
  readonly closeDisabled = input(false);
  readonly close = output<void>();
}
