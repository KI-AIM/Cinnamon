import { Component, Input } from '@angular/core';
import { WorkstepService } from "../../services/workstep.service";

@Component({
    selector: 'app-workstep-title',
    templateUrl: './workstep-title.component.html',
    styleUrl: './workstep-title.component.less',
    standalone: false,
})
export class WorkstepTitleComponent {
    @Input() public stepIndex!: number;

    constructor(
        private readonly workstepService: WorkstepService,
    ) {
    }

    /**
     * Gets the current workstep.
     * @protected
     */
    protected get currentStep(): number {
        return this.workstepService.currentStep;
    }
}
