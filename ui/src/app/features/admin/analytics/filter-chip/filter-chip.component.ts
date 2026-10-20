import {
  afterNextRender,
  Component,
  computed,
  ElementRef,
  inject,
  Injector,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';

/** One choice in a chip's menu; color adds a swatch, as asset classes have. */
export interface ChipOption {
  value: string;
  label: string;
  color?: string;
}

let nextId = 0;

/**
 * A filter as a chip, "Asset class: All ▾", that opens a menu of choices. A dashed outline while it
 * shows everything, filled once it filters. Closes on Escape or a click outside, like the nav bar menus.
 */
@Component({
  selector: 'app-filter-chip',
  templateUrl: './filter-chip.component.html',
  styleUrl: './filter-chip.component.css',
  host: {
    '(document:keydown.escape)': 'close(true)',
    '(document:click)': 'closeOnOutsideClick($event)',
  },
})
export class FilterChipComponent {
  readonly label = input.required<string>();
  readonly options = input.required<readonly ChipOption[]>();
  // null means the "all" choice
  readonly value = input<string | null>(null);
  readonly allLabel = input('All');
  // A search box above the choices, for long lists like traders
  readonly searchable = input(false);
  readonly valueChange = output<string | null>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly button = viewChild.required<ElementRef<HTMLButtonElement>>('button');
  private readonly menu = viewChild<ElementRef<HTMLElement>>('menu');

  readonly menuId = `filter-chip-menu-${nextId++}`;
  readonly isOpen = signal(false);
  readonly query = signal('');

  readonly selected = computed(() => this.options().find((option) => option.value === this.value()) ?? null);
  readonly visibleOptions = computed(() => {
    const query = this.query().trim().toLowerCase();
    return query ? this.options().filter((option) => option.label.toLowerCase().includes(query)) : this.options();
  });

  toggle(): void {
    if (this.isOpen()) {
      this.close(true);
      return;
    }
    this.query.set('');
    this.isOpen.set(true);
    // The menu exists only after the next render; then focus the search box, or the chosen option
    afterNextRender(
      () => {
        const menu = this.menu()?.nativeElement;
        const target =
          menu?.querySelector<HTMLElement>('input') ?? menu?.querySelector<HTMLElement>('[aria-checked="true"]');
        target?.focus();
      },
      { injector: this.injector },
    );
  }

  choose(value: string | null): void {
    if (value !== this.value()) {
      this.valueChange.emit(value);
    }
    this.close(true);
  }

  /** refocus puts focus back on the chip, so keyboard users don't lose their place. */
  close(refocus: boolean): void {
    if (!this.isOpen()) {
      return;
    }
    this.isOpen.set(false);
    if (refocus) {
      this.button().nativeElement.focus();
    }
  }

  closeOnOutsideClick(event: Event): void {
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.close(false);
    }
  }

  onSearch(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }

  // Up and down move between the choices, as in a native select
  moveFocus(event: KeyboardEvent): void {
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') {
      return;
    }
    event.preventDefault();
    const items = Array.from(this.menu()?.nativeElement.querySelectorAll<HTMLElement>('[role="menuitemradio"]') ?? []);
    const current = items.indexOf(this.host.nativeElement.ownerDocument.activeElement as HTMLElement);
    const step = event.key === 'ArrowDown' ? 1 : -1;
    items[(current + step + items.length) % items.length]?.focus();
  }
}
