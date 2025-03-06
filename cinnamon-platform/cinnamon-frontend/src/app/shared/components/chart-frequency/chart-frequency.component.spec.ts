import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ChartFrequencyComponent } from './chart-frequency.component';

describe('ChartFrequencyComponent', () => {
  let component: ChartFrequencyComponent;
  let fixture: ComponentFixture<ChartFrequencyComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ChartFrequencyComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ChartFrequencyComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
