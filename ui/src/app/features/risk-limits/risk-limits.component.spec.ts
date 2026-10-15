import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';

import { RiskLimitControllerService, RiskLimitResponse } from '../../generated/trading-api-client';
import { AccountService } from '../../services/account.service';
import { RiskLimitsComponent } from './risk-limits.component';

const DEFAULTS: RiskLimitResponse[] = [
  { rule: 'SINGLE_INSTRUMENT', thresholdPct: 20, direction: 'ABOVE', enabled: true, source: 'DEFAULT' },
  { rule: 'ASSET_CLASS', thresholdPct: 60, direction: 'ABOVE', enabled: true, source: 'DEFAULT' },
  { rule: 'CASH_FLOOR', thresholdPct: 20, direction: 'BELOW', enabled: true, source: 'DEFAULT' },
];

describe('RiskLimitsComponent', () => {
  const currentAccountId = signal<string | null>('ACC001');
  let api: jasmine.SpyObj<{
    getLimits: (id: string) => Observable<RiskLimitResponse[]>;
    updateLimit: (id: string, rule: string, body: object) => Observable<RiskLimitResponse>;
    resetLimit: (id: string, rule: string) => Observable<RiskLimitResponse>;
  }>;
  let fixture: ComponentFixture<RiskLimitsComponent>;
  let root: HTMLElement;

  beforeEach(() => {
    currentAccountId.set('ACC001');
    api = jasmine.createSpyObj('limits', ['getLimits', 'updateLimit', 'resetLimit']);
    api.getLimits.and.returnValue(of(DEFAULTS));
    TestBed.configureTestingModule({
      imports: [RiskLimitsComponent],
      providers: [
        { provide: RiskLimitControllerService, useValue: api },
        { provide: AccountService, useValue: { currentAccountId } },
      ],
    });
    fixture = TestBed.createComponent(RiskLimitsComponent);
    fixture.detectChanges();
    root = fixture.nativeElement;
  });

  const card = (index: number) => root.querySelectorAll<HTMLElement>('.card')[index];
  const type = (index: number, value: string) => {
    const input = card(index).querySelector('input')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };
  const saveButton = (index: number) => card(index).querySelector<HTMLButtonElement>('button[type=submit]')!;

  it('shows each rule in plain words with its threshold and source', () => {
    expect(api.getLimits).toHaveBeenCalledWith('ACC001');
    expect(root.querySelectorAll('.card').length).toBe(3);
    expect(card(0).textContent).toContain('Warn me when one stock is over');
    expect(card(2).textContent).toContain('Warn me when cash falls below');
    expect(card(2).querySelector('input')!.value).toBe('20');
    expect(card(2).querySelector('.badge')!.textContent).toContain('Default');
    expect(card(2).querySelector('.reset')).toBeNull();
  });

  it('saves a new threshold and shows it as custom with a reset option', () => {
    api.updateLimit.and.returnValue(
      of({ rule: 'CASH_FLOOR', thresholdPct: 40, direction: 'BELOW', enabled: true, source: 'CUSTOM' }),
    );
    expect(saveButton(2).disabled).toBeTrue();
    type(2, '40');
    saveButton(2).click();
    fixture.detectChanges();

    expect(api.updateLimit).toHaveBeenCalledWith('ACC001', 'CASH_FLOOR', { thresholdPct: 40, enabled: true });
    expect(card(2).querySelector('input')!.value).toBe('40');
    expect(card(2).querySelector('.badge')!.textContent).toContain('Custom');
    expect(card(2).querySelector('.reset')).not.toBeNull();
  });

  it('does not allow saving a value outside 0 to 100 or with more than two decimals', () => {
    for (const value of ['101', '-5', 'abc', '12.345', '']) {
      type(0, value);
      expect(saveButton(0).disabled).withContext(value).toBeTrue();
    }
    type(0, '101');
    expect(card(0).querySelector('.field-error')?.textContent).toContain('0 to 100');
    type(0, '12.5');
    expect(saveButton(0).disabled).toBeFalse();
  });

  it('turns a rule off and on with the switch', () => {
    api.updateLimit.and.returnValue(
      of({ rule: 'ASSET_CLASS', thresholdPct: 60, direction: 'ABOVE', enabled: false, source: 'CUSTOM' }),
    );
    const toggle = card(1).querySelector<HTMLButtonElement>('.switch')!;
    expect(toggle.getAttribute('aria-checked')).toBe('true');
    toggle.click();
    fixture.detectChanges();

    expect(api.updateLimit).toHaveBeenCalledWith('ACC001', 'ASSET_CLASS', { thresholdPct: 60, enabled: false });
    expect(card(1).querySelector('.switch')!.getAttribute('aria-checked')).toBe('false');
    expect(card(1).textContent).toContain('This rule never warns you');
    expect(card(1).querySelector('input')).toBeNull();
  });

  it('resets a custom rule to the default', () => {
    api.getLimits.and.returnValue(
      of([DEFAULTS[0], DEFAULTS[1], { ...DEFAULTS[2], thresholdPct: 40, source: 'CUSTOM' }]),
    );
    fixture.componentInstance.retry();
    fixture.detectChanges();
    api.resetLimit.and.returnValue(of(DEFAULTS[2]));

    card(2).querySelector<HTMLButtonElement>('.reset')!.click();
    fixture.detectChanges();

    expect(api.resetLimit).toHaveBeenCalledWith('ACC001', 'CASH_FLOOR');
    expect(card(2).querySelector('input')!.value).toBe('20');
    expect(card(2).querySelector('.badge')!.textContent).toContain('Default');
  });

  it('shows a save error on that rule only and keeps what the user typed', () => {
    api.updateLimit.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'Threshold must be between 0 and 100' } })),
    );
    type(0, '30');
    saveButton(0).click();
    fixture.detectChanges();

    expect(card(0).querySelector('[role=alert]')?.textContent).toContain('Threshold must be between 0 and 100');
    expect(card(0).querySelector('input')!.value).toBe('30');
    expect(card(1).querySelector('[role=alert]')).toBeNull();
  });

  it('disables a rule while it saves', () => {
    const pending = new Subject<RiskLimitResponse>();
    api.updateLimit.and.returnValue(pending);
    type(0, '30');
    saveButton(0).click();
    fixture.detectChanges();

    expect(saveButton(0).textContent).toContain('Saving');
    expect(card(0).querySelector<HTMLButtonElement>('.switch')!.disabled).toBeTrue();
    expect(card(1).querySelector<HTMLButtonElement>('.switch')!.disabled).toBeFalse();
  });

  it('reloads the limits when the account changes', () => {
    currentAccountId.set('ACC002');
    fixture.detectChanges();
    expect(api.getLimits).toHaveBeenCalledWith('ACC002');
  });

  it('offers a retry when the limits cannot load', () => {
    api.getLimits.and.returnValue(throwError(() => new Error('offline')));
    fixture.componentInstance.retry();
    fixture.detectChanges();
    expect(root.querySelector('[role=alert]')?.textContent).toContain("Couldn't load");

    api.getLimits.and.returnValue(of(DEFAULTS));
    root.querySelector<HTMLButtonElement>('.load-error button')!.click();
    fixture.detectChanges();
    expect(root.querySelectorAll('.card').length).toBe(3);
  });
});
