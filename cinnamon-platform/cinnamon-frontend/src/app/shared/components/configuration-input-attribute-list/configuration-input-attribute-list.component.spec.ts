import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigurationInputAttributeListComponent } from './configuration-input-attribute-list.component';

describe('ConfigurationInputAttributeListComponent', () => {
  let component: ConfigurationInputAttributeListComponent;
  let fixture: ComponentFixture<ConfigurationInputAttributeListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ConfigurationInputAttributeListComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ConfigurationInputAttributeListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
