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
const COPY: AccountPieResponse = {
  accountId: 'ACC001',
  pieId: 'p9',
  pieName: 'Steady Compounders',
  mode: 'COPY',
  appliedVersion: 1,
  maxDriftPct: 1,
  published: true,
  updateAvailable: false,
};

const NOT_LINKED = new HttpErrorResponse({ status: 404, error: { errorCode: 'PIE_NOT_LINKED' } });

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
    makePieMine: (id: string) => Observable<AccountPieResponse>;
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
    accountPies = jasmine.createSpyObj('accountPies', ['getAccountPie', 'linkPie', 'unlinkPie', 'makePieMine']);
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
    accountPies.getAccountPie.and.returnValue(throwError(() => NOT_LINKED));
    create();
    expect(root.querySelector('.header')!.textContent).toContain('No strategy on this account yet');
  });

  it('keeps a linked pie whose prices are missing, with no drift', () => {
    accountPies.getAccountPie.and.returnValue(of({ ...LINKED, maxDriftPct: undefined, drift: [] }));
    create();
    const header = root.querySelector('.header')!;
    expect(header.textContent).toContain('This account follows');
    expect(header.textContent).not.toContain('max drift');
    expect(button('Unlink')).toBeTruthy();
  });

  it('shows an error, not the no-pie header, for other 404s', () => {
    accountPies.getAccountPie.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 404, error: { errorCode: 'ACCOUNT_NOT_FOUND' } })),
    );
    create();
    expect(root.querySelector('.header')!.textContent).toContain("Couldn't load this account's strategy.");
  });

  it('unlinks the pie and shows the no-pie header', () => {
    accountPies.unlinkPie.and.returnValue(of(null));
    create();
    accountPies.getAccountPie.and.returnValue(throwError(() => NOT_LINKED));
    button('Unlink').click();
    fixture.detectChanges();
    expect(accountPies.unlinkPie).toHaveBeenCalledWith('ACC001');
    expect(root.querySelector('.header')!.textContent).toContain('No strategy on this account yet');
  });

  it('links the pie for this account, then opens the preview', () => {
    accountPies.linkPie.and.returnValue(of(LINKED));
    create();
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl');
    button('Use for this account').click();
    expect(accountPies.linkPie).toHaveBeenCalledWith('ACC001', { pieId: 'p1' });
    expect(navigate).toHaveBeenCalledWith('/accounts/ACC001/rebalance');
  });

  it('links each pie name to its details, and the tabs to Discover', () => {
    create();
    expect(root.querySelector('.pie-card h2 a')!.getAttribute('href')).toBe('/strategies/p1');
    expect(root.querySelector('.pie-card .public')).toBeNull();
    expect(Array.from(root.querySelectorAll('app-pie-tabs a')).map((a) => a.getAttribute('href'))).toEqual([
      '/strategies',
      '/discover',
    ]);
  });

  it('marks published pies', () => {
    pies.listMine.and.returnValue(of([{ ...PIE, publishedAt: '2026-03-02T09:00:00Z' }]));
    create();
    expect(root.querySelector('.pie-card .public')!.textContent).toBe('Public');
  });

  it('links to new and edit', () => {
    create();
    expect(root.querySelector('a.new')!.getAttribute('href')).toBe('/strategies/new');
    expect(root.querySelector('.pie-card a.edit')!.getAttribute('href')).toBe('/strategies/p1/edit');
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
    expect(root.textContent).toContain(
      "Build your first strategy: pick stocks and weights, and we'll work out the trades.",
    );
  });

  describe('a copied pie', () => {
    const header = () => root.querySelector('.header')!;
    const link = (text: string) =>
      Array.from(header().querySelectorAll('a')).find((a) => a.textContent?.trim() === text) as HTMLAnchorElement;

    it('says the account copies it, with copy controls instead of unlink', () => {
      accountPies.getAccountPie.and.returnValue(of(COPY));
      create();
      expect(header().textContent).toContain('This account copies');
      expect(header().querySelector('input[role="switch"]')).toBeNull();
      expect(button('Make it mine')).toBeTruthy();
      expect(button('Stop copying')).toBeTruthy();
      expect(button('Unlink')).toBeUndefined();
      expect(header().querySelector('.status')).toBeNull();
    });

    it('offers a review when an author update is available', () => {
      accountPies.getAccountPie.and.returnValue(of({ ...COPY, updateAvailable: true }));
      create();
      expect(header().querySelector('.status')?.textContent).toContain('Update available');
      expect(link('Review').getAttribute('href')).toBe('/accounts/ACC001/rebalance');
    });

    it('links to the active rebalance without offering another review', () => {
      accountPies.getAccountPie.and.returnValue(of({ ...COPY, updateAvailable: true, activeRunId: 'run7' }));
      create();
      expect(header().querySelector('.status')).toBeNull();
      expect(link('Review')).toBeUndefined();
      expect(link('View rebalance').getAttribute('href')).toBe('/rebalance-runs/run7');
      expect(button('Make it mine').disabled).toBeTrue();
    });

    it("can't be made the user's own before anything is applied", () => {
      accountPies.getAccountPie.and.returnValue(of({ ...COPY, appliedVersion: undefined, updateAvailable: true }));
      create();
      expect(button('Make it mine').disabled).toBeTrue();
    });

    it('says when the author stopped publishing it', () => {
      accountPies.getAccountPie.and.returnValue(of({ ...COPY, published: false }));
      create();
      expect(header().querySelector('.status')?.textContent).toContain('No longer published');
    });

    it("makes the pie the user's own and reloads", () => {
      accountPies.getAccountPie.and.returnValue(of(COPY));
      accountPies.makePieMine.and.returnValue(of({ ...COPY, mode: 'OWN' }));
      create();
      accountPies.getAccountPie.and.returnValue(of({ ...LINKED, pieName: 'Steady Compounders' }));
      button('Make it mine').click();
      fixture.detectChanges();
      expect(accountPies.makePieMine).toHaveBeenCalledOnceWith('ACC001');
      expect(pies.listMine).toHaveBeenCalledTimes(2);
      expect(header().textContent).toContain('This account follows');
    });

    it("shows why the pie could not be made the user's own", () => {
      accountPies.getAccountPie.and.returnValue(of(COPY));
      accountPies.makePieMine.and.returnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 409,
              error: { message: 'Nothing from the copied pie has been applied to this account yet' },
            }),
        ),
      );
      create();
      button('Make it mine').click();
      fixture.detectChanges();
      expect(root.querySelector('[role="alert"]')?.textContent).toContain('Nothing from the copied pie');
    });

    it('stops copying after confirming, keeping holdings', () => {
      accountPies.getAccountPie.and.returnValue(of(COPY));
      accountPies.unlinkPie.and.returnValue(of(null));
      const confirm = spyOn(window, 'confirm').and.returnValue(true);
      create();
      button('Stop copying').click();
      expect(confirm.calls.mostRecent().args[0]).toContain('Your holdings stay as they are');
      expect(accountPies.unlinkPie).toHaveBeenCalledOnceWith('ACC001');
    });
  });
});
