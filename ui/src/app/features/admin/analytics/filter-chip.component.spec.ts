import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ChipOption, FilterChipComponent } from './filter-chip.component';

const OPTIONS: ChipOption[] = [
  { value: 'EQUITY', label: 'Equity', color: 'var(--asset-equity-color)' },
  { value: 'ETF', label: 'ETF' },
  { value: 'BOND', label: 'Bond' },
];

describe('FilterChipComponent', () => {
  let fixture: ComponentFixture<FilterChipComponent>;
  let emitted: (string | null)[];

  function create(value: string | null = null, searchable = false): HTMLElement {
    TestBed.configureTestingModule({ imports: [FilterChipComponent] });
    fixture = TestBed.createComponent(FilterChipComponent);
    fixture.componentRef.setInput('label', 'Asset class');
    fixture.componentRef.setInput('options', OPTIONS);
    fixture.componentRef.setInput('value', value);
    fixture.componentRef.setInput('searchable', searchable);
    emitted = [];
    fixture.componentInstance.valueChange.subscribe((v) => emitted.push(v));
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  function chip(page: HTMLElement): HTMLButtonElement {
    return page.querySelector('.chip') as HTMLButtonElement;
  }

  function open(page: HTMLElement): void {
    chip(page).click();
    fixture.detectChanges();
  }

  function options(page: HTMLElement): string[] {
    return Array.from(page.querySelectorAll('[role="menuitemradio"]')).map((el) => el.textContent?.trim() ?? '');
  }

  it('shows the label and "All" while nothing is chosen, dashed rather than filled', () => {
    const page = create();

    expect(chip(page).textContent).toContain('Asset class: All');
    expect(chip(page).classList).not.toContain('set');
    expect(chip(page).getAttribute('aria-expanded')).toBe('false');
    expect(page.querySelector('[role="menu"]')).toBeNull();
  });

  it('shows the chosen option and fills the chip', () => {
    const page = create('ETF');

    expect(chip(page).textContent).toContain('Asset class: ETF');
    expect(chip(page).classList).toContain('set');
  });

  it('opens a menu with "All" and every option, the current one checked', () => {
    const page = create('BOND');
    open(page);

    expect(chip(page).getAttribute('aria-expanded')).toBe('true');
    expect(options(page)).toEqual(['All', 'Equity', 'ETF', 'Bond']);
    const checked = page.querySelector('[aria-checked="true"]');
    expect(checked?.textContent?.trim()).toBe('Bond');
    expect(page.querySelector('.swatch')).not.toBeNull();
  });

  it('emits the chosen value and closes', () => {
    const page = create();
    open(page);

    (page.querySelectorAll('[role="menuitemradio"]')[2] as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(emitted).toEqual(['ETF']);
    expect(page.querySelector('[role="menu"]')).toBeNull();
  });

  it('emits null for "All"', () => {
    const page = create('ETF');
    open(page);

    (page.querySelector('[role="menuitemradio"]') as HTMLButtonElement).click();

    expect(emitted).toEqual([null]);
  });

  it("doesn't emit when the current option is chosen again", () => {
    const page = create('ETF');
    open(page);

    (page.querySelectorAll('[role="menuitemradio"]')[2] as HTMLButtonElement).click();

    expect(emitted).toEqual([]);
  });

  it('closes on Escape and gives focus back to the chip', () => {
    const page = create();
    document.body.appendChild(page);
    open(page);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(page.querySelector('[role="menu"]')).toBeNull();
    expect(document.activeElement).toBe(chip(page));
    page.remove();
  });

  it('closes on a click outside, but not on a click inside', () => {
    const page = create(null, true);
    open(page);

    (page.querySelector('.search') as HTMLInputElement).click();
    fixture.detectChanges();
    expect(page.querySelector('[role="menu"]')).not.toBeNull();

    document.body.click();
    fixture.detectChanges();
    expect(page.querySelector('[role="menu"]')).toBeNull();
  });

  it('filters the options with the search box, keeping "All"', () => {
    const page = create(null, true);
    open(page);

    const search = page.querySelector('.search') as HTMLInputElement;
    search.value = 'e';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(options(page)).toEqual(['All', 'Equity', 'ETF']);

    search.value = 'zzz';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(options(page)).toEqual(['All']);
    expect(page.querySelector('.none')?.textContent).toContain('No match');
  });

  it('moves focus between the options with the arrow keys, wrapping round', () => {
    const page = create();
    document.body.appendChild(page);
    open(page);
    const items = Array.from(page.querySelectorAll<HTMLElement>('[role="menuitemradio"]'));
    const menu = page.querySelector('[role="menu"]') as HTMLElement;

    items[0].focus();
    menu.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    expect(document.activeElement).toBe(items[1]);

    items[0].focus();
    menu.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true }));
    expect(document.activeElement).toBe(items[3]);
    page.remove();
  });
});
