import { ComponentFixture, TestBed } from '@angular/core/testing';

import { StringpatternComponent } from './stringpattern.component';

describe('StringpatternComponent', () => {
  let component: StringpatternComponent;
  let fixture: ComponentFixture<StringpatternComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ StringpatternComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(StringpatternComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
