import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, of, Subject } from 'rxjs';
import { AccountControllerService, AccountResponse } from '../../generated/trading-api-client';
import { AccountService } from '../../services/account.service';
import { CashActionsComponent } from './cash-actions.component';

describe('CashActionsComponent', () => {
  let api: jasmine.SpyObj<{ deposit: (id: string, body: { amount: number }) => Observable<AccountResponse>; withdraw: (id: string, body: { amount: number }) => Observable<AccountResponse> }>;
  const account: AccountResponse = { accountId: 'ACC001', holderName: 'Trader', cashBalance: 100, status: 'ACTIVE' };
  function setup() {
    api = jasmine.createSpyObj('AccountControllerService', ['deposit', 'withdraw']);
    api.deposit.and.returnValue(of({ ...account, cashBalance: 110 }));
    api.withdraw.and.returnValue(of({ ...account, cashBalance: 90 }));
    TestBed.configureTestingModule({ imports: [CashActionsComponent], providers: [
      { provide: AccountControllerService, useValue: api },
      { provide: AccountService, useValue: { accounts: signal([account]) } },
    ] });
    const fixture = TestBed.createComponent(CashActionsComponent);
    fixture.componentRef.setInput('accountId', 'ACC001'); fixture.componentRef.setInput('cashBalance', 100);
    fixture.detectChanges(); return fixture;
  }
  it('opens a modal, focuses the amount and restores focus when cancelled', () => {
    const fixture=setup(), root:HTMLElement=fixture.nativeElement;
    const trigger=root.querySelector<HTMLButtonElement>('.actions button')!;
    trigger.focus();trigger.click();fixture.detectChanges();
    const dialog=root.querySelector<HTMLDialogElement>('dialog')!;
    expect(dialog.open).toBeTrue();
    expect(document.activeElement).toBe(root.querySelector('#cash-amount'));
    fixture.componentInstance.cancel(new Event('cancel',{cancelable:true}));fixture.detectChanges();
    expect(dialog.open).toBeFalse();
    expect(document.activeElement).toBe(trigger);
    expect(api.deposit).not.toHaveBeenCalled();
  });
  it('keeps the modal open if Escape is pressed during a transfer', () => {
    const fixture=setup(), component=fixture.componentInstance;
    const pending=new Subject<AccountResponse>();api.deposit.and.returnValue(pending);
    component.open('deposit');fixture.detectChanges();component.amount.setValue(10);component.submit();
    const event=new Event('cancel',{cancelable:true});component.cancel(event);
    expect(event.defaultPrevented).toBeTrue();expect(component.mode()).toBe('deposit');
    pending.next({...account,cashBalance:110});pending.complete();fixture.detectChanges();
    expect(rootDialogOpen()).toBeFalse();
    function rootDialogOpen(){return fixture.nativeElement.querySelector('dialog').open;}
  });
  it('blocks invalid amounts and withdrawals beyond available cash', () => {
    const component = setup().componentInstance;
    component.open('deposit');
    for (const amount of [null, 0, -1, 1.001]) { component.amount.setValue(amount); component.submit(); }
    expect(api.deposit).not.toHaveBeenCalled();
    component.open('withdraw'); component.amount.setValue(101); component.submit();
    expect(api.withdraw).not.toHaveBeenCalled(); expect(component.error()).toContain('available cash');
  });
  it('sends the selected account and amount, updates account state and emits completion', () => {
    const component = setup().componentInstance;
    const completed = jasmine.createSpy('completed'); component.completed.subscribe(completed);
    component.open('deposit'); component.amount.setValue(10); component.submit();
    expect(api.deposit).toHaveBeenCalledOnceWith('ACC001', { amount: 10 });
    expect(TestBed.inject(AccountService).accounts()[0].cashBalance).toBe(110);
    expect(completed).toHaveBeenCalledTimes(1); expect(component.mode()).toBeNull();
    component.open('withdraw'); component.amount.setValue(10); component.submit();
    expect(api.withdraw).toHaveBeenCalledOnceWith('ACC001', { amount: 10 });
  });
  it('prevents duplicate submissions and reports errors without losing the amount', () => {
    const component = setup().componentInstance;
    const pending = new Subject<AccountResponse>(); api.deposit.and.returnValue(pending);
    component.open('deposit'); component.amount.setValue(10); component.submit(); component.submit();
    expect(api.deposit).toHaveBeenCalledTimes(1);
    pending.error(new Error('offline'));
    expect(component.submitting()).toBeFalse(); expect(component.amount.value).toBe(10);
    expect(component.error()).toContain("Couldn't complete");
  });
  it('clears the form on account change without applying an old response to the new account', () => {
    const fixture = setup(); const component = fixture.componentInstance;
    const pending = new Subject<AccountResponse>(); api.deposit.and.returnValue(pending);
    const completed = jasmine.createSpy('completed'); component.completed.subscribe(completed);
    component.open('deposit'); component.amount.setValue(10); component.submit();
    fixture.componentRef.setInput('accountId', 'ACC002'); fixture.detectChanges();
    expect(component.mode()).toBeNull();
    pending.next({ ...account, cashBalance: 110 }); pending.complete();
    expect(completed).not.toHaveBeenCalled(); expect(component.success()).toBeNull();
  });
});
