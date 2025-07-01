import { ComponentFixture, TestBed } from '@angular/core/testing';

import { WorkstepTitleComponent } from './workstep-title.component';

describe('WorkstepTitleComponent', () => {
  let component: WorkstepTitleComponent;
  let fixture: ComponentFixture<WorkstepTitleComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkstepTitleComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(WorkstepTitleComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
