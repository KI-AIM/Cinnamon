/* tslint:disable:no-unused-variable */
import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';

import { AnonymizationAttributeConfigurationComponent } from './anonymization-attribute-configuration.component';

describe('AnonymizationAttributeConfigurationComponent', () => {
  let component: AnonymizationAttributeConfigurationComponent;
  let fixture: ComponentFixture<AnonymizationAttributeConfigurationComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AnonymizationAttributeConfigurationComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AnonymizationAttributeConfigurationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
