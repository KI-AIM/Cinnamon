import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Steps } from '@core/enums/steps';
import { StatusService } from '@shared/services/status.service';
import { WorkstepService } from '@shared/services/workstep.service';
import { WorkstepListComponent } from './workstep-list.component';

describe('WorkstepListComponent', () => {
    let fixture: ComponentFixture<WorkstepListComponent>;
    let worksteps: WorkstepService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [WorkstepListComponent],
            providers: [{provide: StatusService, useValue: {isStepCompleted: () => false}}],
        }).compileComponents();
        fixture = TestBed.createComponent(WorkstepListComponent);
        worksteps = TestBed.inject(WorkstepService);
        fixture.componentRef.setInput('step', Steps.SYNTHETIZATION);
        fixture.componentRef.setInput('numberSteps', 7);
        fixture.componentRef.setInput('confirmLabel', 'Confirm configuration');
        fixture.detectChanges();
    });

    it('allows confirmation after the remaining four steps when structured synthesis is disabled', () => {
        worksteps.confirmStep(0);
        fixture.componentRef.setInput('numberSteps', 4);
        fixture.detectChanges();
        expect(worksteps.currentStep).toBe(1);
        expect(fixture.nativeElement.querySelector('button').disabled).toBeTrue();

        worksteps.confirmStep(3);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('button').disabled).toBeFalse();
    });

    it('requires the additional steps again when structured synthesis is enabled', () => {
        fixture.componentRef.setInput('numberSteps', 4);
        fixture.detectChanges();
        worksteps.confirmStep(3);
        fixture.componentRef.setInput('numberSteps', 7);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('button').disabled).toBeTrue();

        worksteps.confirmStep(6);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('button').disabled).toBeFalse();
    });

    it('keeps invalid configurations and locked steps blocked after reducing the step count', () => {
        fixture.componentRef.setInput('numberSteps', 4);
        worksteps.confirmStep(3);
        fixture.componentRef.setInput('invalid', true);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('button').disabled).toBeTrue();

        fixture.componentRef.setInput('invalid', false);
        fixture.componentRef.setInput('locked', true);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('button').disabled).toBeTrue();
    });
});
