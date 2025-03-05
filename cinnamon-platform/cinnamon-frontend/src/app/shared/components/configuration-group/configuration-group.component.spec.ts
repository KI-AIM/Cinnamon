import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigurationGroupComponent } from './configuration-group.component';

describe('ConfigurationGroupComponent', () => {
  let component: ConfigurationGroupComponent;
  let fixture: ComponentFixture<ConfigurationGroupComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ConfigurationGroupComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ConfigurationGroupComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
