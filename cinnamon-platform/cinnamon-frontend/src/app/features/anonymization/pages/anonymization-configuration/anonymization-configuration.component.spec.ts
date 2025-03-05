import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AnonymizationConfigurationComponent } from './anonymization-configuration.component';

describe('AnonymizationConfigurationComponent', () => {
  let component: AnonymizationConfigurationComponent;
  let fixture: ComponentFixture<AnonymizationConfigurationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ AnonymizationConfigurationComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AnonymizationConfigurationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
