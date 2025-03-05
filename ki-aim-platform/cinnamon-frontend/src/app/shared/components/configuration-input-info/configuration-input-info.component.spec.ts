import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigurationInputInfoComponent } from './configuration-input-info.component';

describe('ConfigurationInputInfoComponent', () => {
  let component: ConfigurationInputInfoComponent;
  let fixture: ComponentFixture<ConfigurationInputInfoComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ConfigurationInputInfoComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ConfigurationInputInfoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
