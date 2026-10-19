import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';

import { PublicPieControllerService, PublicPieSummary } from '../../generated/trading-api-client';
import { DiscoverComponent } from './discover.component';

const PIES: PublicPieSummary[] = [
  {
    pieId: 'p1',
    name: 'Steady Compounders',
    authorUsername: 'ada',
    sliceCount: 4,
    returnPct: 12.5,
    followers: 1200,
    demo: true,
  },
  { pieId: 'p2', name: 'Chip Bet', authorUsername: 'grace', sliceCount: 2, returnPct: -3.456, followers: 7 },
  { pieId: 'p3', name: 'Fresh', authorUsername: 'linus', sliceCount: 1, followers: 0 },
];

describe('DiscoverComponent', () => {
  let api: jasmine.SpyObj<{ listPublicPies: (window?: string) => Observable<PublicPieSummary[]> }>;
  let fixture: ComponentFixture<DiscoverComponent>;
  let root: HTMLElement;

  function create(): void {
    fixture = TestBed.createComponent(DiscoverComponent);
    fixture.detectChanges();
    root = fixture.nativeElement;
  }

  const button = (text: string) =>
    Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.trim() === text) as HTMLButtonElement;

  beforeEach(() => {
    api = jasmine.createSpyObj('publicPies', ['listPublicPies']);
    api.listPublicPies.and.returnValue(of(PIES));
    TestBed.configureTestingModule({
      imports: [DiscoverComponent],
      providers: [provideRouter([]), { provide: PublicPieControllerService, useValue: api }],
    });
  });

  it('ranks the pies for ALL by default', () => {
    create();
    expect(api.listPublicPies).toHaveBeenCalledOnceWith('ALL');
    const rows = Array.from(root.querySelectorAll('.entry'));
    expect(rows.map((r) => r.querySelector('h2 a')!.textContent)).toEqual(['Steady Compounders', 'Chip Bet', 'Fresh']);
    expect(rows.map((r) => r.querySelector('.rank')!.textContent)).toEqual(['1', '2', '3']);
  });

  it('shows author, return, followers, slices and the demo label', () => {
    create();
    const [first, second, third] = Array.from(root.querySelectorAll('.entry')) as HTMLElement[];
    expect(first.textContent).toContain('by ada');
    expect(first.querySelector('.tag')!.textContent).toBe('Demo');
    expect(first.querySelector('.main dd')!.textContent!.trim()).toBe('+12.50%');
    expect(first.querySelector('.main dd')!.classList).toContain('gain');
    expect(first.textContent).toContain('1,200');
    expect(second.querySelector('.tag')).toBeNull();
    expect(second.querySelector('.main dd')!.textContent!.trim()).toBe('-3.46%');
    expect(second.querySelector('.main dd')!.classList).toContain('loss');
    expect(third.querySelector('.main dd')!.textContent!.trim()).toBe('—');
  });

  it('links each pie to its public page', () => {
    create();
    expect(root.querySelector('.entry h2 a')!.getAttribute('href')).toBe('/discover/p1');
  });

  it('links the tabs', () => {
    create();
    const links = Array.from(root.querySelectorAll('app-pie-tabs a'));
    expect(links.map((a) => a.getAttribute('href'))).toEqual(['/pies', '/discover']);
  });

  it('reloads for the chosen window', () => {
    create();
    button('30D').click();
    fixture.detectChanges();
    expect(api.listPublicPies).toHaveBeenCalledWith('30D');
    expect(button('30D').getAttribute('aria-pressed')).toBe('true');
    expect(button('ALL').getAttribute('aria-pressed')).toBe('false');
    expect(root.querySelector('.metric.main dt')!.textContent).toBe('30D return');
  });

  it('shows a loading state', () => {
    api.listPublicPies.and.returnValue(new Subject<PublicPieSummary[]>());
    create();
    expect(root.querySelector('output')!.textContent).toContain('Loading public pies');
    expect(root.querySelector('.entry')).toBeNull();
  });

  it('explains an empty window', () => {
    api.listPublicPies.and.returnValue(of([]));
    create();
    button('90D').click();
    fixture.detectChanges();
    expect(root.textContent).toContain('No pie has been public for 90D yet');
  });

  it('explains an empty leaderboard', () => {
    api.listPublicPies.and.returnValue(of([]));
    create();
    expect(root.textContent).toContain('No public pies yet.');
  });

  it('shows an error and retries', () => {
    api.listPublicPies.and.returnValue(throwError(() => new Error('down')));
    create();
    expect(root.querySelector('[role="alert"]')!.textContent).toContain("Couldn't load public pies");
    api.listPublicPies.and.returnValue(of(PIES));
    button('Try again').click();
    fixture.detectChanges();
    expect(root.querySelector('[role="alert"]')).toBeNull();
    expect(root.querySelectorAll('.entry').length).toBe(3);
  });

  it('keeps the chosen window while its request is in flight', () => {
    create();
    api.listPublicPies.and.returnValue(new Subject<PublicPieSummary[]>());
    button('7D').click();
    fixture.detectChanges();
    expect(button('7D').getAttribute('aria-pressed')).toBe('true');
    expect(root.querySelector('.entry')).toBeNull();
  });
});
