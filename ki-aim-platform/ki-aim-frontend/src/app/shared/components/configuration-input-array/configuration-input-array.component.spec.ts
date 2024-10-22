import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigurationInputArrayComponent } from './configuration-input-array.component';

describe('ConfigurationInputArrayComponent', () => {
  let component: ConfigurationInputArrayComponent;
  let fixture: ComponentFixture<ConfigurationInputArrayComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ConfigurationInputArrayComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ConfigurationInputArrayComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
