import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DatetimeformatComponent } from './datetimeformat.component';

describe('DatetimeformatComponent', () => {
  let component: DatetimeformatComponent;
  let fixture: ComponentFixture<DatetimeformatComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ DatetimeformatComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DatetimeformatComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
