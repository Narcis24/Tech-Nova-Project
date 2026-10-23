import { Component, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TIME_IN_FORCE_OPTIONS, TimeInForce } from '../../core/time-in-force';

/**
 * How long a limit order stays open: today, until cancelled, or until a date the user picks. Works on
 * the order ticket's own form controls, which also hold the date's validation.
 */
@Component({
  selector: 'app-time-in-force-picker',
  imports: [ReactiveFormsModule],
  templateUrl: './time-in-force-picker.component.html',
  styleUrl: './time-in-force-picker.component.css',
})
export class TimeInForcePickerComponent {
  readonly timeInForce = input.required<FormControl<TimeInForce>>();
  readonly expiresOn = input.required<FormControl<string | null>>();
  /** The earliest and latest dates a GTD order can run to, as YYYY-MM-DD. */
  readonly min = input.required<string>();
  readonly max = input.required<string>();
  readonly options = TIME_IN_FORCE_OPTIONS;

  showDateError(): boolean {
    const date = this.expiresOn();
    return date.invalid && (date.dirty || date.touched);
  }
}
