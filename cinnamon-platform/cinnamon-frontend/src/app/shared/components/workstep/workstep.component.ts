import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { MatExpansionPanel } from "@angular/material/expansion";
import { WorkstepService } from "../../services/workstep.service";
import { Subscription } from "rxjs";

@Component({
    selector: 'app-workstep',
    templateUrl: './workstep.component.html',
    styleUrl: './workstep.component.less',
    standalone: false
})
export class WorkstepComponent implements OnInit, OnDestroy {
    @Input() public stepIndex!: number;
    @Input() public locked!: boolean;
    @Input() public valid!: boolean;
    @Input() public altConfirm: string | null = null;

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
     * Confirms the current step, closes the expansion panel, and goes to the next step.
     * @protected
     */
    protected confirmStep(): void {
        this.matExpansionPanel.close();
        this.workstepService.confirmStep(this.stepIndex);
    }
}
