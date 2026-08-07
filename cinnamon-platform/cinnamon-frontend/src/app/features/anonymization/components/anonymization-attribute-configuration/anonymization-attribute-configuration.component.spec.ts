import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { FormArray, FormBuilder } from '@angular/forms';
import { of } from 'rxjs';
import { AnonymizationAttributeConfigurationComponent } from './anonymization-attribute-configuration.component';
import { AnonymizationAttributeConfigurationService } from '../../services/anonymization-attribute-configuration.service';
import { DataConfigurationService } from 'src/app/shared/services/data-configuration.service';

describe('AnonymizationAttributeConfigurationComponent', () => {
  let component: AnonymizationAttributeConfigurationComponent;
  let fixture: ComponentFixture<AnonymizationAttributeConfigurationComponent>;
  let formBuilder: FormBuilder;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [AnonymizationAttributeConfigurationComponent],
      providers: [
        FormBuilder,
        AnonymizationAttributeConfigurationService,
        {
          provide: DataConfigurationService,
          useValue: {
            dataConfiguration$: of({ configurations: [] }),
          },
        },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    })
    .compileComponents();
  }));

  beforeEach(() => {
    formBuilder = TestBed.inject(FormBuilder);
    fixture = TestBed.createComponent(AnonymizationAttributeConfigurationComponent);
    component = fixture.componentInstance;
    component.disabled = false;
    component.form = formBuilder.group({
      attributeConfiguration: new FormArray([]),
    });
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
