import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';

import { PieControllerService, PieResponse } from '../../generated/trading-api-client';
import { PieDetailComponent } from './pie-detail.component';

const PRIVATE: PieResponse = {
  pieId: 'p1',
  name: 'Tech Growth',
  description: 'My core pie',
  cashPct: 10,
  slices: [
    { symbol: 'AAPL', name: 'Apple', weightPct: 50 },
    { symbol: 'MSFT', name: 'Microsoft', weightPct: 40 },
  ],
};
const PUBLIC: PieResponse = { ...PRIVATE, publishedAt: '2026-03-02T09:00:00Z' };

/** The visible text of each child, as a row would read aloud. */
const cells = (el: Element) =>
  Array.from(el.children)
    .map((c) => c.textContent!.trim())
    .filter(Boolean)
    .join(' ');

describe('PieDetailComponent', () => {
  let api: jasmine.SpyObj<{
    get: (id: string) => Observable<PieResponse>;
    publishPie: (id: string) => Observable<PieResponse>;
    unpublishPie: (id: string) => Observable<PieResponse>;
  }>;
  let fixture: ComponentFixture<PieDetailComponent>;
  let root: HTMLElement;

  function create(): void {
    fixture = TestBed.createComponent(PieDetailComponent);
    fixture.detectChanges();
    root = fixture.nativeElement;
  }

  const button = (text: string) =>
    Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.trim() === text) as HTMLButtonElement;

  beforeEach(() => {
    api = jasmine.createSpyObj('pies', ['get', 'publishPie', 'unpublishPie']);
    api.get.and.returnValue(of(PRIVATE));
    TestBed.configureTestingModule({
      imports: [PieDetailComponent],
      providers: [
        provideRouter([]),
        { provide: PieControllerService, useValue: api },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'p1' } } } },
      ],
    });
  });

  it('shows the pie with its slices and cash', () => {
    create();
    expect(root.querySelector('h1')!.textContent).toBe('Tech Growth');
    expect(root.querySelector('.description')!.textContent).toBe('My core pie');
    const rows = Array.from(root.querySelectorAll('.holdings li')).map(cells);
    expect(rows).toEqual(['AAPL Apple 50%', 'MSFT Microsoft 40%', 'Cash 10%']);
    expect(root.querySelector('a.edit')!.getAttribute('href')).toBe('/pies/p1/edit');
    expect(root.querySelector('.back')!.getAttribute('href')).toBe('/pies');
  });

  it('offers to publish a private pie', () => {
    create();
    expect(root.querySelector('.publishing h2')!.textContent).toBe('Private');
    expect(button('Publish')).toBeDefined();
    expect(root.querySelector('.publishing')!.textContent).toContain('stocks and weights on Discover');
    expect(button('Unpublish')).toBeUndefined();
    expect(root.querySelector('.publishing a')).toBeNull();
  });

  it('publishes and links to the public page', () => {
    api.publishPie.and.returnValue(of(PUBLIC));
    create();
    button('Publish').click();
    fixture.detectChanges();
    expect(api.publishPie).toHaveBeenCalledWith('p1');
    expect(root.querySelector('.publishing h2')!.textContent).toBe('Public');
    expect(root.querySelector('.publishing')!.textContent).toContain('Mar 2, 2026');
    expect(root.querySelector('.publishing a')!.getAttribute('href')).toBe('/discover/p1');
    expect(button('Unpublish')).toBeDefined();
  });

  it('unpublishes a public pie', () => {
    api.get.and.returnValue(of(PUBLIC));
    api.unpublishPie.and.returnValue(of(PRIVATE));
    create();
    expect(root.querySelector('.publishing a')).not.toBeNull();
    button('Unpublish').click();
    fixture.detectChanges();
    expect(api.unpublishPie).toHaveBeenCalledWith('p1');
    expect(root.querySelector('.publishing h2')!.textContent).toBe('Private');
    expect(root.querySelector('.publishing a')).toBeNull();
  });

  it('disables the button while publishing', () => {
    api.publishPie.and.returnValue(new Subject<PieResponse>());
    create();
    button('Publish').click();
    fixture.detectChanges();
    expect(button('Publish').disabled).toBeTrue();
  });

  it('keeps the state and shows an error when publishing fails', () => {
    api.publishPie.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    create();
    button('Publish').click();
    fixture.detectChanges();
    expect(root.querySelector('[role="alert"]')!.textContent).toContain("Couldn't publish the strategy");
    expect(root.querySelector('.publishing h2')!.textContent).toBe('Private');
    expect(button('Publish').disabled).toBeFalse();
  });

  it('shows an error when unpublishing fails', () => {
    api.get.and.returnValue(of(PUBLIC));
    api.unpublishPie.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    create();
    button('Unpublish').click();
    fixture.detectChanges();
    expect(root.querySelector('[role="alert"]')!.textContent).toContain("Couldn't unpublish the strategy");
    expect(root.querySelector('.publishing h2')!.textContent).toBe('Public');
  });

  it('shows a loading state', () => {
    api.get.and.returnValue(new Subject<PieResponse>());
    create();
    expect(root.querySelector('output')!.textContent).toContain('Loading your strategy');
  });

  it('shows an error when the pie cannot be loaded', () => {
    api.get.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    create();
    expect(root.querySelector('[role="alert"]')!.textContent).toContain("Couldn't load this strategy");
    expect(root.querySelector('h1')).toBeNull();
  });
});
