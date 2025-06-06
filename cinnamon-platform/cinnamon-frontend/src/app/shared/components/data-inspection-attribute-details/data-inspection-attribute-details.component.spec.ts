import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DataInspectionAttributeDetailsComponent } from './data-inspection-attribute-details.component';

describe('DataInspectionAttributeDetailsComponent', () => {
  let component: DataInspectionAttributeDetailsComponent;
  let fixture: ComponentFixture<DataInspectionAttributeDetailsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ DataInspectionAttributeDetailsComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DataInspectionAttributeDetailsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
