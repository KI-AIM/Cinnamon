import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TechnicalEvaluationConfigurationComponent } from './technical-evaluation-configuration.component';

describe('TechnicalEvaluationConfigurationComponent', () => {
  let component: TechnicalEvaluationConfigurationComponent;
  let fixture: ComponentFixture<TechnicalEvaluationConfigurationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ TechnicalEvaluationConfigurationComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TechnicalEvaluationConfigurationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
