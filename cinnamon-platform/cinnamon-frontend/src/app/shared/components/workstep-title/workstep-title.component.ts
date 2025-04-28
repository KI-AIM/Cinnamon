import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { WorkstepService } from "../../services/workstep.service";
import { Subscription } from "rxjs";
import { MatExpansionPanel } from "@angular/material/expansion";

@Component({
    selector: 'app-workstep-title',
    templateUrl: './workstep-title.component.html',
    styleUrl: './workstep-title.component.less',
    standalone: false,
})
export class WorkstepTitleComponent implements OnInit, OnDestroy {
    @Input() public stepIndex!: number;

    private stepSubscription: Subscription;

    constructor(
        private readonly workstepService: WorkstepService,
        private readonly matExpansionPanel: MatExpansionPanel,
    ) {
    }

    public ngOnInit(): void {
        this.stepSubscription = this.workstepService.step$.subscribe(step => {
            if (this.stepIndex === step) {
                this.matExpansionPanel.open();
            }
        });
    }

    public ngOnDestroy(): void {
        this.stepSubscription.unsubscribe();
    }

    /**
     * Gets the current workstep.
     * @protected
     */
    protected get currentStep(): number {
        return this.workstepService.currentStep;
    }

    /**
     * If all worksteps have been completed in the past.
     * @protected
     */
    protected get finished(): boolean {
        return this.workstepService.isFinished;
    }
}
