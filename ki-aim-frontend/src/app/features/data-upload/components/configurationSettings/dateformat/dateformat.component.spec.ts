import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DateformatComponent } from './dateformat.component';

describe('DateformatComponent', () => {
  let component: DateformatComponent;
  let fixture: ComponentFixture<DateformatComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ DateformatComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DateformatComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
