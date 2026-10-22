import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';

import { PublicPieControllerService, PublicPieResponse } from '../../generated/trading-api-client';
import { AccountService } from '../../services/account.service';
import { AuthService } from '../../services/auth.service';
import { PublicPieComponent } from './public-pie.component';

const PIE: PublicPieResponse = {
  pieId: 'p1',
  name: 'Steady Compounders',
  description: 'Slow and boring',
  authorUsername: 'ada',
  sliceCount: 2,
  followers: 1200,
  publishedAt: '2026-03-02T09:00:00Z',
  demo: true,
  cashPct: 10,
  slices: [
    { symbol: 'AAPL', name: 'Apple', weightPct: 50 },
    { symbol: 'MSFT', name: 'Microsoft', weightPct: 40 },
  ],
};

/** The visible text of each child, as a row would read aloud. */
const cells = (el: Element) =>
  Array.from(el.children)
    .map((c) => c.textContent!.trim())
    .filter(Boolean)
    .join(' ');

describe('PublicPieComponent', () => {
  let api: jasmine.SpyObj<{ getPublicPie: (id: string) => Observable<PublicPieResponse> }>;
  const username = signal<string | null>('grace');
  const accounts = signal([{ accountId: 'acc1', holderName: 'Grace ISA' }]);
  const currentAccountId = signal<string | null>('acc1');
  let fixture: ComponentFixture<PublicPieComponent>;
  let root: HTMLElement;

  function create(): void {
    fixture = TestBed.createComponent(PublicPieComponent);
    fixture.detectChanges();
    root = fixture.nativeElement;
  }

  beforeEach(() => {
    username.set('grace');
    currentAccountId.set('acc1');
    api = jasmine.createSpyObj('publicPies', ['getPublicPie']);
    api.getPublicPie.and.returnValue(of(PIE));
    TestBed.configureTestingModule({
      imports: [PublicPieComponent],
      providers: [
        provideRouter([]),
        { provide: PublicPieControllerService, useValue: api },
        { provide: AuthService, useValue: { username } },
        { provide: AccountService, useValue: { accounts, currentAccountId } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'p1' } } } },
      ],
    });
  });

  it('shows the pie, its author and the demo label', () => {
    create();
    expect(api.getPublicPie).toHaveBeenCalledWith('p1');
    expect(root.querySelector('h1')!.textContent).toBe('Steady Compounders');
    expect(root.querySelector('.tag')!.textContent).toBe('Demo strategy');
    expect(root.querySelector('.byline')!.textContent).toContain('by ada');
    expect(root.querySelector('.byline')!.textContent).toContain('Mar 2, 2026');
    expect(root.querySelector('.description')!.textContent).toBe('Slow and boring');
    expect(root.querySelector('.back')!.getAttribute('href')).toBe('/discover');
  });

  it('shows followers without performance rankings or history', () => {
    create();
    expect(Array.from(root.querySelectorAll('.stats > div')).map(cells)).toEqual(['Followers 1,200']);
    expect(root.querySelector('app-index-chart')).toBeNull();
    expect(root.textContent).not.toContain('return');
  });

  it('lists the slices and cash', () => {
    create();
    const rows = Array.from(root.querySelectorAll('.holdings li')).map(cells);
    expect(rows).toEqual(['AAPL Apple 50%', 'MSFT Microsoft 40%', 'Cash 10%']);
    expect(root.querySelector('app-donut-chart')).not.toBeNull();
  });

  it("does not show any of the author's account details", () => {
    create();
    root.querySelector('.copy')?.remove(); // names the viewer's own account
    expect(root.textContent).not.toMatch(/ACC|account|\$/i);
  });

  it('explains a pie that is not public', () => {
    api.getPublicPie.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    create();
    expect(root.textContent).toContain("This strategy isn't public");
    expect(root.querySelector('[role="alert"]')).toBeNull();
  });

  it('shows a loading state', () => {
    api.getPublicPie.and.returnValue(new Subject<PublicPieResponse>());
    create();
    expect(root.querySelector('output')!.textContent).toContain('Loading strategy');
  });

  it('shows an error and retries', () => {
    api.getPublicPie.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    create();
    expect(root.querySelector('[role="alert"]')!.textContent).toContain("Couldn't load this strategy");
    api.getPublicPie.and.returnValue(of(PIE));
    (root.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(root.querySelector('h1')!.textContent).toBe('Steady Compounders');
  });

  it('copies into the current account through the preview', () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    create();
    expect(root.querySelector('.copy')?.textContent).toContain('Into account acc1');
    expect(root.querySelector('.copy')?.textContent).toContain('your review and confirmation');
    (root.querySelector('.copy button') as HTMLButtonElement).click();
    expect(navigate).toHaveBeenCalledWith(['/accounts', 'acc1', 'rebalance'], { queryParams: { copy: 'p1' } });
  });

  it("doesn't offer to copy your own pie", () => {
    username.set('ada');
    create();
    expect(root.querySelector('.copy')).toBeNull();
  });

  it('asks for an account before copying', () => {
    currentAccountId.set(null);
    create();
    expect(root.querySelector('.copy button')).toBeNull();
    expect(root.querySelector('.copy')?.textContent).toContain('Open an account');
  });
});
