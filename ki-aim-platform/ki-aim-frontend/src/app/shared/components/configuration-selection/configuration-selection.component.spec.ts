import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigurationSelectionComponent } from './configuration-selection.component';

describe('ConfigurationSelectionComponent', () => {
  let component: ConfigurationSelectionComponent;
  let fixture: ComponentFixture<ConfigurationSelectionComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ConfigurationSelectionComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ConfigurationSelectionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
