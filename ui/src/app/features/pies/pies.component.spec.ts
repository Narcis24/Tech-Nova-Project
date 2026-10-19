import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import {
  AccountPieControllerService,
  AccountPieResponse,
  PieControllerService,
  PieResponse,
} from '../../generated/trading-api-client';
import { AccountService } from '../../services/account.service';
import { PiesComponent } from './pies.component';

const PIE: PieResponse = {
  pieId: 'p1',
  name: 'Tech Growth',
  cashPct: 10,
  slices: [
    { symbol: 'AAPL', name: 'Apple', weightPct: 50 },
    { symbol: 'MSFT', name: 'Microsoft', weightPct: 40 },
  ],
};
const LINKED: AccountPieResponse = { accountId: 'ACC001', pieId: 'p1', pieName: 'Tech Growth', maxDriftPct: 3.5 };

describe('PiesComponent', () => {
  const currentAccountId = signal<string | null>('ACC001');
  let pies: jasmine.SpyObj<{
    listMine: () => Observable<PieResponse[]>;
    deletePie: (id: string) => Observable<unknown>;
  }>;
  let accountPies: jasmine.SpyObj<{
    getAccountPie: (id: string) => Observable<AccountPieResponse>;
    linkPie: (id: string, body: { pieId: string }) => Observable<AccountPieResponse>;
    unlinkPie: (id: string) => Observable<unknown>;
  }>;
  let fixture: ComponentFixture<PiesComponent>;
  let root: HTMLElement;

  function create(): void {
    fixture = TestBed.createComponent(PiesComponent);
    fixture.detectChanges();
    root = fixture.nativeElement;
  }

  const button = (text: string) =>
    Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.trim() === text) as HTMLButtonElement;

  beforeEach(() => {
    currentAccountId.set('ACC001');
    pies = jasmine.createSpyObj('pies', ['listMine', 'deletePie']);
    accountPies = jasmine.createSpyObj('accountPies', ['getAccountPie', 'linkPie', 'unlinkPie']);
    pies.listMine.and.returnValue(of([PIE]));
    accountPies.getAccountPie.and.returnValue(of(LINKED));
    TestBed.configureTestingModule({
      imports: [PiesComponent],
      providers: [
        provideRouter([]),
        { provide: PieControllerService, useValue: pies },
        { provide: AccountPieControllerService, useValue: accountPies },
        { provide: AccountService, useValue: { currentAccountId } },
      ],
    });
  });

  it('lists the pies with their top slices and cash', () => {
    create();
    const card = root.querySelector('.pie-card')!;
    expect(card.textContent).toContain('Tech Growth');
    expect(card.textContent).toContain('AAPL');
    expect(card.textContent).toContain('Cash 10%');
    expect(card.querySelector('app-donut-chart')).not.toBeNull();
  });

  it('shows the linked pie header with a preview link', () => {
    create();
    const header = root.querySelector('.header')!;
    expect(header.textContent).toContain('This account follows');
    expect(header.textContent).toContain('Tech Growth');
    expect(header.textContent).toContain('3.5%');
    expect(header.querySelector('a')!.getAttribute('href')).toBe('/accounts/ACC001/rebalance');
  });

  it('shows the no-pie header on 404', () => {
    accountPies.getAccountPie.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    create();
    expect(root.querySelector('.header')!.textContent).toContain('No pie on this account yet');
  });

  it('unlinks the pie and shows the no-pie header', () => {
    accountPies.unlinkPie.and.returnValue(of(null));
    create();
    accountPies.getAccountPie.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    button('Unlink').click();
    fixture.detectChanges();
    expect(accountPies.unlinkPie).toHaveBeenCalledWith('ACC001');
    expect(root.querySelector('.header')!.textContent).toContain('No pie on this account yet');
  });

  it('links the pie for this account, then opens the preview', () => {
    accountPies.linkPie.and.returnValue(of(LINKED));
    create();
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl');
    button('Use for this account').click();
    expect(accountPies.linkPie).toHaveBeenCalledWith('ACC001', { pieId: 'p1' });
    expect(navigate).toHaveBeenCalledWith('/accounts/ACC001/rebalance');
  });

  it('links to new and edit', () => {
    create();
    expect(root.querySelector('a.new')!.getAttribute('href')).toBe('/pies/new');
    expect(root.querySelector('.pie-card a.edit')!.getAttribute('href')).toBe('/pies/p1/edit');
  });

  it('deletes after confirming', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    pies.deletePie.and.returnValue(of(null));
    create();
    pies.listMine.and.returnValue(of([] as PieResponse[]));
    button('Delete').click();
    fixture.detectChanges();
    expect(pies.deletePie).toHaveBeenCalledWith('p1');
    expect(root.querySelector('.pie-card')).toBeNull();
  });

  it('does not delete when the confirm is declined', () => {
    spyOn(window, 'confirm').and.returnValue(false);
    create();
    button('Delete').click();
    expect(pies.deletePie).not.toHaveBeenCalled();
  });

  it('explains a 409 on delete', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    pies.deletePie.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    create();
    button('Delete').click();
    fixture.detectChanges();
    expect(root.querySelector('[role="alert"]')!.textContent).toContain('Unlink it from your accounts first');
    expect(root.querySelector('.pie-card')).not.toBeNull();
  });

  it('shows the empty state with no pies', () => {
    pies.listMine.and.returnValue(of([] as PieResponse[]));
    create();
    expect(root.textContent).toContain("Build your first pie: pick stocks and weights, and we'll work out the trades.");
  });
});
