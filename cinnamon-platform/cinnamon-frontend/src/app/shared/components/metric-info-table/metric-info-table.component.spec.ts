import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MetricInfoTableComponent } from './metric-info-table.component';

describe('MetricInfoTableComponent', () => {
  let component: MetricInfoTableComponent;
  let fixture: ComponentFixture<MetricInfoTableComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ MetricInfoTableComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(MetricInfoTableComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
