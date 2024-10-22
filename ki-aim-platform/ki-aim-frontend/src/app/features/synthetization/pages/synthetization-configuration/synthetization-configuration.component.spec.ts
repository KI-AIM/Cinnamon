import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SynthetizationConfigurationComponent } from './synthetization-configuration.component';

describe('SynthetizationConfigurationComponent', () => {
  let component: SynthetizationConfigurationComponent;
  let fixture: ComponentFixture<SynthetizationConfigurationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ SynthetizationConfigurationComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SynthetizationConfigurationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
