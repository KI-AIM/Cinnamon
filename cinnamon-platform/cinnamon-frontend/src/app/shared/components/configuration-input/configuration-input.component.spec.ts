import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subscription } from 'rxjs';

import { ConfigurationInputComponent } from './configuration-input.component';

describe('ConfigurationInputComponent', () => {
  let component: ConfigurationInputComponent;
  let fixture: ComponentFixture<ConfigurationInputComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ConfigurationInputComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ConfigurationInputComponent);
    component = fixture.componentInstance;
    (component as any).dataConfigurationSubscription = new Subscription();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
