import { TestBed } from '@angular/core/testing';
import { FormControl, Validators } from '@angular/forms';
import { TimeInForce } from '../../core/time-in-force';
import { TimeInForcePickerComponent } from './time-in-force-picker.component';

describe('TimeInForcePickerComponent', () => {
  function render() {
    const timeInForce = new FormControl<TimeInForce>('DAY', { nonNullable: true }),
      expiresOn = new FormControl<string | null>(null, Validators.required);
    const fixture = TestBed.createComponent(TimeInForcePickerComponent);
    fixture.componentRef.setInput('timeInForce', timeInForce);
    fixture.componentRef.setInput('expiresOn', expiresOn);
    fixture.componentRef.setInput('min', '2026-10-24');
    fixture.componentRef.setInput('max', '2027-01-21');
    fixture.detectChanges();
    return { fixture, root: fixture.nativeElement as HTMLElement, timeInForce, expiresOn };
  }
  const option = (root: HTMLElement, label: string) =>
    Array.from(root.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.includes(label))!;

  it('offers the three choices as a labelled group, today chosen', () => {
    const { root } = render();
    expect(root.querySelector('fieldset legend')?.textContent).toBe('Good for');
    expect(Array.from(root.querySelectorAll('button strong')).map((s) => s.textContent)).toEqual([
      'Today',
      'Until cancelled',
      'Until a date',
    ]);
    expect(option(root, 'Today').getAttribute('aria-pressed')).toBe('true');
    expect(root.querySelector('input[type="date"]')).toBeNull();
  });

  it('sets the choice and asks for a date only for Until a date', () => {
    const { fixture, root, timeInForce, expiresOn } = render();
    option(root, 'Until cancelled').click();
    fixture.detectChanges();
    expect(timeInForce.value).toBe('GTC');
    expect(root.querySelector('input[type="date"]')).toBeNull();

    option(root, 'Until a date').click();
    fixture.detectChanges();
    const date = root.querySelector<HTMLInputElement>('input[type="date"]')!;
    expect(date.min).toBe('2026-10-24');
    expect(date.max).toBe('2027-01-21');
    expect(root.querySelector('.error')?.textContent?.trim()).toBe('');

    expiresOn.markAsTouched();
    fixture.detectChanges();
    expect(date.getAttribute('aria-invalid')).toBe('true');
    expect(root.querySelector('.error')?.textContent).toContain('Pick a date');

    date.value = '2026-11-02';
    date.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(expiresOn.value).toBe('2026-11-02');
    expect(date.getAttribute('aria-invalid')).toBe('false');
  });
});
