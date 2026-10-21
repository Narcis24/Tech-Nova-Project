import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SymbolIconComponent } from './symbol-icon.component';

describe('SymbolIconComponent', () => {
  let fixture: ComponentFixture<SymbolIconComponent>;

  beforeEach(() => {
    fixture = TestBed.createComponent(SymbolIconComponent);
    fixture.componentRef.setInput('symbol', 'AAPL');
    fixture.detectChanges();
  });

  it('shows the logo for the symbol', () => {
    const img: HTMLImageElement = fixture.nativeElement.querySelector('img');

    expect(img.src).toBe('https://financialmodelingprep.com/image-stock/AAPL.png');
  });

  it('falls back to the initials when the logo fails to load', () => {
    fixture.nativeElement.querySelector('img').dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    expect(fixture.nativeElement.querySelector('.initials').textContent).toBe('AA');
  });

  it('tries the logo again when the symbol changes', () => {
    fixture.nativeElement.querySelector('img').dispatchEvent(new Event('error'));
    fixture.componentRef.setInput('symbol', 'MSFT');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('img').src).toContain('MSFT.png');
  });
});
