import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DataConfigurationComponent } from './data-configuration.component';

describe('DataConfigurationComponent', () => {
  let component: DataConfigurationComponent;
  let fixture: ComponentFixture<DataConfigurationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ DataConfigurationComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DataConfigurationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
