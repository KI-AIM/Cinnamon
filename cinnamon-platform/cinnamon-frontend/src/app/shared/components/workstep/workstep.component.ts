import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatExpansionPanel } from "@angular/material/expansion";
import { WorkstepService } from "../../services/workstep.service";

@Component({
    selector: 'app-workstep',
    templateUrl: './workstep.component.html',
    styleUrl: './workstep.component.less',
    standalone: false
})
export class WorkstepComponent {
    @Input() public stepIndex!: number;
    @Input() public locked!: boolean;
    @Input() public valid: boolean = true;

    @Input() public altConfirm: string | null = null;
    @Input() public altConfirmValid: boolean = true;
    @Input() public altConfirmAll: boolean = false;

    @Output() public confirm = new EventEmitter<void>();
    @Output() public confirmAlt = new EventEmitter<void>();

    constructor(
        private readonly workstepService: WorkstepService,
        private readonly matExpansionPanel: MatExpansionPanel,
    ) {
    }

    /**
     * Confirms the current step, closes the expansion panel, and goes to the next step.
     * @protected
     */
    protected confirmStep(): void {
        this.matExpansionPanel.close();
        this.workstepService.confirmStep(this.stepIndex);
        this.confirm.emit();
    }

    /**
     * Confirms the current step, closes the expansion panel, and goes to the next step or alternatively completes all steps.
     * @protected
     */
    protected altConfirmStep(): void {
        this.matExpansionPanel.close();
        if (this.altConfirmAll) {
            this.workstepService.confirmAllSteps();
        } else {
            this.workstepService.confirmStep(this.stepIndex);
        }
        this.confirmAlt.emit();
    }
}
