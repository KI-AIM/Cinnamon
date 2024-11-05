import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ChartDensityComponent } from './chart-density.component';

describe('ChartDensityComponent', () => {
  let component: ChartDensityComponent;
  let fixture: ComponentFixture<ChartDensityComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ChartDensityComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ChartDensityComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
