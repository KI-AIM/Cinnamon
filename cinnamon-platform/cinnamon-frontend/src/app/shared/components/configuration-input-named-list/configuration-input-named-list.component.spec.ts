import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormArray, FormGroup } from '@angular/forms';

import { ConfigurationInputNamedListComponent } from './configuration-input-named-list.component';

describe('ConfigurationInputNamedListComponent', () => {
  let component: ConfigurationInputNamedListComponent;
  let fixture: ComponentFixture<ConfigurationInputNamedListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ConfigurationInputNamedListComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ConfigurationInputNamedListComponent);
    component = fixture.componentInstance;
    component.configurationInputDefinition = {
      name: 'required_attributes',
    } as any;
    component.parentForm = new FormGroup({
      required_attributes: new FormArray([]),
    });
    component.disabled = false;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
