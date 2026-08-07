import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatExpansionPanel } from '@angular/material/expansion';
import { Subject, Subscription } from 'rxjs';

import { WorkstepTitleComponent } from './workstep-title.component';

describe('WorkstepTitleComponent', () => {
  let component: WorkstepTitleComponent;
  let fixture: ComponentFixture<WorkstepTitleComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [WorkstepTitleComponent],
      providers: [
        {
          provide: MatExpansionPanel,
          useValue: {
            opened: new Subject<void>(),
            open: jasmine.createSpy('open'),
            close: jasmine.createSpy('close'),
          },
        },
      ],
    })
    .compileComponents();

    fixture = TestBed.createComponent(WorkstepTitleComponent);
    component = fixture.componentInstance;
    (component as any).openedExpansionPanelSubscription = new Subscription();
    (component as any).openedStepSubscription = new Subscription();
    (component as any).stepSubscription = new Subscription();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
