import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import {
  InstrumentControllerService,
  InstrumentResponse,
  PieControllerService,
  PieResponse,
  SavePieRequest,
} from '../../generated/trading-api-client';
import { PieEditorComponent } from './pie-editor.component';

const INSTRUMENTS: InstrumentResponse[] = [
  { symbol: 'AAPL', name: 'Apple' },
  { symbol: 'MSFT', name: 'Microsoft' },
];
const PIE: PieResponse = {
  pieId: 'p1',
  name: 'Tech Growth',
  description: 'Big tech',
  cashPct: 10,
  slices: [
    { symbol: 'AAPL', name: 'Apple', weightPct: 50 },
    { symbol: 'MSFT', name: 'Microsoft', weightPct: 40 },
  ],
};

describe('PieEditorComponent', () => {
  let pies: jasmine.SpyObj<{
    get: (id: string) => Observable<PieResponse>;
    create: (body: SavePieRequest) => Observable<PieResponse>;
    update: (id: string, body: SavePieRequest) => Observable<PieResponse>;
  }>;
  let fixture: ComponentFixture<PieEditorComponent>;
  let root: HTMLElement;
  let navigate: jasmine.Spy;

  function create(pieId: string | null = null): void {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: { snapshot: { paramMap: { get: () => pieId } } },
    });
    fixture = TestBed.createComponent(PieEditorComponent);
    navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    fixture.detectChanges();
    root = fixture.nativeElement;
  }

  function type(el: HTMLInputElement | HTMLSelectElement, value: string): void {
    el.value = value;
    el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? 'change' : 'input'));
    fixture.detectChanges();
  }

  const name = () => root.querySelector<HTMLInputElement>('#name')!;
  const symbols = () => Array.from(root.querySelectorAll<HTMLSelectElement>('.row select'));
  const weights = () => Array.from(root.querySelectorAll<HTMLInputElement>('.row input'));
  const button = (text: string) =>
    Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.trim() === text) as HTMLButtonElement;

  function fillRow(index: number, symbol: string, weight: string): void {
    while (weights().length <= index) {
      button('Add stock').click();
      fixture.detectChanges();
    }
    type(symbols()[index], symbol);
    type(weights()[index], weight);
  }

  beforeEach(() => {
    pies = jasmine.createSpyObj('pies', ['get', 'create', 'update']);
    pies.get.and.returnValue(of(PIE));
    pies.create.and.returnValue(of(PIE));
    pies.update.and.returnValue(of(PIE));
    TestBed.configureTestingModule({
      imports: [PieEditorComponent],
      providers: [
        provideRouter([]),
        { provide: PieControllerService, useValue: pies },
        { provide: InstrumentControllerService, useValue: { getInstruments: () => of(INSTRUMENTS) } },
      ],
    });
  });

  it('computes cash from the weights', () => {
    create();
    fillRow(0, 'AAPL', '50');
    fillRow(1, 'MSFT', '33.5');

    expect(root.querySelector('.cash-line')?.textContent).toContain('Cash: 16.5%');
  });

  it('disables save when the weights add up to over 100', () => {
    create();
    type(name(), 'Mine');
    fillRow(0, 'AAPL', '60');
    expect(button('Save').disabled).toBeFalse();

    fillRow(1, 'MSFT', '41');
    expect(button('Save').disabled).toBeTrue();
  });

  it('disables save on a duplicate symbol', () => {
    create();
    type(name(), 'Mine');
    fillRow(0, 'AAPL', '10');
    fillRow(1, 'AAPL', '10');

    expect(button('Save').disabled).toBeTrue();
  });

  it('creates with the normalised body and returns to the list', () => {
    create();
    type(name(), '  Mine ');
    fillRow(0, 'AAPL', '50.50');
    button('Save').click();

    expect(pies.create).toHaveBeenCalledWith({
      name: 'Mine',
      description: undefined,
      slices: [{ symbol: 'AAPL', weightPct: 50.5 }],
    });
    expect(navigate).toHaveBeenCalledWith('/pies');
  });

  it('loads the pie in edit mode, then updates it', () => {
    create('p1');
    expect(pies.get).toHaveBeenCalledWith('p1');
    expect(name().value).toBe('Tech Growth');
    expect(weights().map((w) => w.value)).toEqual(['50', '40']);

    type(weights()[0], '45');
    button('Save').click();

    expect(pies.update).toHaveBeenCalledWith('p1', {
      name: 'Tech Growth',
      description: 'Big tech',
      slices: [
        { symbol: 'AAPL', weightPct: 45 },
        { symbol: 'MSFT', weightPct: 40 },
      ],
    });
    expect(navigate).toHaveBeenCalledWith('/pies');
  });

  it('shows the server message on a 422', () => {
    pies.create.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 422,
            error: { errorCode: 'INVALID_PIE', message: 'Symbol is not tradable' },
          }),
      ),
    );
    create();
    type(name(), 'Mine');
    fillRow(0, 'AAPL', '50');
    button('Save').click();
    fixture.detectChanges();

    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Symbol is not tradable');
    expect(navigate).not.toHaveBeenCalled();
  });
});
