import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Observable, of, Subject, throwError } from 'rxjs';
import { PriceControllerService, PriceHistoryPoint } from '../../generated/trading-api-client';
import { PriceChartComponent } from './price-chart.component';

describe('PriceChartComponent', () => {
  let api: jasmine.SpyObj<{ getPriceHistory:(symbol:string)=>Observable<PriceHistoryPoint[]> }>;
  const points = [
    {date:'2025-05-01',close:80}, {date:'2026-01-31',close:90},
    {date:'2026-02-28',close:95}, {date:'2026-03-01',close:98}, {date:'2026-03-31',close:100},
  ];
  function setup(symbol='AAPL') {
    api=jasmine.createSpyObj('prices',['getPriceHistory']);api.getPriceHistory.and.returnValue(of(points));
    TestBed.configureTestingModule({providers:[{provide:PriceControllerService,useValue:api}]});
    const fixture=TestBed.createComponent(PriceChartComponent);fixture.componentRef.setInput('symbol',symbol);fixture.detectChanges();return fixture;
  }
  it('shows the latest close, actual date, daily change, and calendar-month ranges', fakeAsync(() => {
    const fixture=setup();tick(250);fixture.detectChanges();const component=fixture.componentInstance;
    expect(api.getPriceHistory).toHaveBeenCalledOnceWith('AAPL');
    expect(fixture.nativeElement.textContent).toContain('$100.00');expect(fixture.nativeElement.textContent).toContain('Mar 31, 2026');
    expect(component.dailyChange()).toBe(2);
    expect(component.visible().map(p=>p.date)).toEqual(['2026-02-28','2026-03-01','2026-03-31']);
    component.chooseRange(3);expect(component.visible().length).toBe(4);component.chooseRange(12);expect(component.visible().length).toBe(5);
    expect(api.getPriceHistory).toHaveBeenCalledTimes(1);
  }));
  it('cancels an old symbol request and never labels its data as the new instrument', fakeAsync(() => {
    const fixture=setup();const first=new Subject<PriceHistoryPoint[]>();api.getPriceHistory.and.returnValue(first);
    tick(250);fixture.componentRef.setInput('symbol','MSFT');fixture.detectChanges();
    expect(fixture.componentInstance.points()).toEqual([]);
    first.next(points);first.complete();api.getPriceHistory.and.returnValue(of([{date:'2026-03-31',close:200}]));
    tick(250);fixture.detectChanges();expect(fixture.componentInstance.latest()?.close).toBe(200);
    expect(fixture.nativeElement.textContent).toContain('MSFT');expect(fixture.nativeElement.textContent).not.toContain('$100.00');
  }));
  it('can retry a failed request', fakeAsync(() => {
    const fixture=setup();api.getPriceHistory.and.returnValue(throwError(()=>new Error('offline')));tick(250);fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain("Couldn't load AAPL");
    api.getPriceHistory.and.returnValue(of(points));fixture.componentInstance.refresh();fixture.detectChanges();tick(250);fixture.detectChanges();
    expect(fixture.componentInstance.state().error).toBeFalse();expect(fixture.nativeElement.textContent).toContain('$100.00');
  }));
  it('handles flat prices and keyboard inspection without invalid coordinates', fakeAsync(() => {
    const fixture=setup();api.getPriceHistory.and.returnValue(of([{date:'2026-03-30',close:5},{date:'2026-03-31',close:5}]));tick(250);fixture.detectChanges();
    const component=fixture.componentInstance;expect(component.line()).not.toMatch(/NaN|Infinity/);expect(component.y(5)).toBe(104);
    component.move(new KeyboardEvent('keydown',{key:'Home'}));expect(component.selected()?.date).toBe('2026-03-30');
    component.move(new KeyboardEvent('keydown',{key:'ArrowRight'}));expect(component.selected()?.date).toBe('2026-03-31');
  }));
  it('shows empty history instead of inventing a chart', fakeAsync(() => {
    const fixture=setup();api.getPriceHistory.and.returnValue(of([]));tick(250);fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No stored price history');expect(fixture.nativeElement.querySelector('svg')).toBeNull();
  }));
  it('waits for an instrument before requesting history', fakeAsync(() => {
    const fixture=setup('');tick(500);fixture.detectChanges();expect(api.getPriceHistory).not.toHaveBeenCalled();expect(fixture.nativeElement.textContent).toContain('Choose an instrument');
  }));
});
