import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { AnonymizationAttributeRowComponent } from './anonymization-attribute-row.component';
import { AnonymizationAttributeConfigurationService } from '../../services/anonymization-attribute-configuration.service';
import { AttributeProtection } from 'src/app/shared/model/anonymization-attribute-config';
import { DataScale } from 'src/app/shared/model/data-scale';
import { DataType } from 'src/app/shared/model/data-type';

describe('AnonymizationAttributeRowComponent', () => {
  let component: AnonymizationAttributeRowComponent;
  let fixture: ComponentFixture<AnonymizationAttributeRowComponent>;
  let formBuilder: FormBuilder;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [AnonymizationAttributeRowComponent],
      providers: [
        FormBuilder,
        AnonymizationAttributeConfigurationService,
      ],
      schemas: [NO_ERRORS_SCHEMA],
    })
    .compileComponents();
  }));

  beforeEach(() => {
    formBuilder = TestBed.inject(FormBuilder);
    fixture = TestBed.createComponent(AnonymizationAttributeRowComponent);
    component = fixture.componentInstance;
    component.disabled = false;
    component.parentForm = formBuilder.group({});
    component.form = formBuilder.group({
      attributeProtection: [AttributeProtection.ATTRIBUTE_DELETION],
      dataType: [DataType.INTEGER],
      index: [0],
      intervalSize: [null],
      name: ['age'],
      scale: [DataScale.RATIO],
    });
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
