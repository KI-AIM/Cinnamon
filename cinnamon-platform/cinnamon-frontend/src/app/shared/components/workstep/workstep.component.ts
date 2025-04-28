import { Component, Input } from '@angular/core';
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
    @Input() public valid!: boolean;
    @Input() public altConfirm: string | null = null;

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
    }
}
