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
    followers: 1200,
    demo: true,
  },
  { pieId: 'p2', name: 'Chip Bet', authorUsername: 'grace', sliceCount: 2, followers: 7 },
  { pieId: 'p3', name: 'Fresh', authorUsername: 'linus', sliceCount: 1, followers: 0 },
];

describe('DiscoverComponent', () => {
  let api: jasmine.SpyObj<{ listPublicPies: () => Observable<PublicPieSummary[]> }>;
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

  it('loads public allocation templates without a performance window', () => {
    create();
    expect(api.listPublicPies).toHaveBeenCalledOnceWith();
    const rows = Array.from(root.querySelectorAll('.entry'));
    expect(rows.map((r) => r.querySelector('h2 a')!.textContent)).toEqual(['Steady Compounders', 'Chip Bet', 'Fresh']);
  });

  it('shows author, followers, slice count and demo label', () => {
    create();
    const [first, second] = Array.from(root.querySelectorAll('.entry'));
    expect(first.textContent).toContain('by ada');
    expect(first.querySelector('.tag')!.textContent).toBe('Demo');
    expect(first.textContent).toContain('1,200');
    expect(first.textContent).toContain('Slices');
    expect(second.querySelector('.tag')).toBeNull();
    expect(root.querySelector('[aria-label="Return window"]')).toBeNull();
    expect(root.querySelector('.rank')).toBeNull();
  });

  it('links each pie to its public page', () => {
    create();
    expect(root.querySelector('.entry h2 a')!.getAttribute('href')).toBe('/discover/p1');
  });

  it('links the tabs', () => {
    create();
    const links = Array.from(root.querySelectorAll('app-pie-tabs a'));
    expect(links.map((a) => a.getAttribute('href'))).toEqual(['/strategies', '/discover']);
  });

  it('shows a loading state', () => {
    api.listPublicPies.and.returnValue(new Subject<PublicPieSummary[]>());
    create();
    expect(root.querySelector('output')!.textContent).toContain('Loading public strategies');
    expect(root.querySelector('.entry')).toBeNull();
  });

  it('explains an empty Discover page', () => {
    api.listPublicPies.and.returnValue(of([]));
    create();
    expect(root.textContent).toContain('No public strategies yet.');
  });

  it('shows an error and retries', () => {
    api.listPublicPies.and.returnValue(throwError(() => new Error('down')));
    create();
    expect(root.querySelector('[role="alert"]')!.textContent).toContain("Couldn't load public strategies");
    api.listPublicPies.and.returnValue(of(PIES));
    button('Try again').click();
    fixture.detectChanges();
    expect(root.querySelector('[role="alert"]')).toBeNull();
    expect(root.querySelectorAll('.entry').length).toBe(3);
  });
});
