import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MetricConfigurationComponent } from './metric-configuration.component';

describe('MetricConfigurationComponent', () => {
  let component: MetricConfigurationComponent;
  let fixture: ComponentFixture<MetricConfigurationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ MetricConfigurationComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(MetricConfigurationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
