import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AddedConfigurationListComponent } from './added-configuration-list.component';

describe('AddedConfigurationListComponent', () => {
  let component: AddedConfigurationListComponent;
  let fixture: ComponentFixture<AddedConfigurationListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ AddedConfigurationListComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AddedConfigurationListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
