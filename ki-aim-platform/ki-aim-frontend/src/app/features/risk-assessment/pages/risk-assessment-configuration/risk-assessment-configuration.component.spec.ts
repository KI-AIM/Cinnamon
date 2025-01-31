import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RiskAssessmentConfigurationComponent } from './risk-assessment-configuration.component';

describe('RiskAssessmentConfigurationComponent', () => {
  let component: RiskAssessmentConfigurationComponent;
  let fixture: ComponentFixture<RiskAssessmentConfigurationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ RiskAssessmentConfigurationComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(RiskAssessmentConfigurationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
