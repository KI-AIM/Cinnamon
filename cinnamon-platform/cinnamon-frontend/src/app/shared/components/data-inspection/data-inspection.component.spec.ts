import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DataInspectionComponent } from './data-inspection.component';

describe('DataInspectionComponent', () => {
  let component: DataInspectionComponent;
  let fixture: ComponentFixture<DataInspectionComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ DataInspectionComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DataInspectionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
