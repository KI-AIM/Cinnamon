import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigurationManagementComponent } from './configuration-management.component';

describe('ConfigurationManagementComponent', () => {
  let component: ConfigurationManagementComponent;
  let fixture: ComponentFixture<ConfigurationManagementComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ConfigurationManagementComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ConfigurationManagementComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
