/* tslint:disable:no-unused-variable */
import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';

import { AnonymizationAttributeRowComponent } from './anonymization-attribute-row.component';

describe('AnonymizationAttributeRowComponent', () => {
  let component: AnonymizationAttributeRowComponent;
  let fixture: ComponentFixture<AnonymizationAttributeRowComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AnonymizationAttributeRowComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AnonymizationAttributeRowComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
