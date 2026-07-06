import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormArray, FormGroup } from '@angular/forms';
import { NotificationService } from '@core/services/notification.service';

import { ConfigurationInputNamedListComponent } from './configuration-input-named-list.component';
import { ErrorHandlingService } from '../../services/error-handling.service';
import { SynthetizationService } from '../../../features/synthetization/services/synthetization.service';

describe('ConfigurationInputNamedListComponent', () => {
  let component: ConfigurationInputNamedListComponent;
  let fixture: ComponentFixture<ConfigurationInputNamedListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ConfigurationInputNamedListComponent ],
      providers: [
        { provide: ErrorHandlingService, useValue: { addError: jasmine.createSpy('addError') } },
        { provide: NotificationService, useValue: { addNotification: jasmine.createSpy('addNotification') } },
        { provide: SynthetizationService, useValue: { suggestNamedList: jasmine.createSpy('suggestNamedList') } },
      ],
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
