import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DataInspectionAttributeComponent } from './data-inspection-attribute.component';

describe('DataInspectionAttributeComponent', () => {
  let component: DataInspectionAttributeComponent;
  let fixture: ComponentFixture<DataInspectionAttributeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ DataInspectionAttributeComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DataInspectionAttributeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
