import { booleanAttribute, Component, EventEmitter, Input, Output } from '@angular/core';
import { WorkstepService } from "@shared/services/workstep.service";

@Component({
  selector: 'app-workstep-item',
  standalone: false,
  templateUrl: './workstep-item.component.html',
  styleUrl: './workstep-item.component.less'
})
export class WorkstepItemComponent {

    @Input() public stepIndex!: number;
    @Input({transform: booleanAttribute}) public invalid: boolean = false;
    @Input({transform: booleanAttribute}) public locked: boolean = false;
    @Input() public header!: string;

    @Input() public altConfirm: string | null = null;
    @Input() public altConfirmValid: boolean = true;
    @Input() public altConfirmAll: boolean = false;

    /**
     * If this workstep should be skipped.
     * Meaning it will not be displayed and directly goes to the next step if opened.
     */
    @Input() public skip: boolean = false;

    @Output() public confirm = new EventEmitter<void>();
    @Output() public confirmAlt = new EventEmitter<void>();

    public constructor(
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

    /**
     * Checks if all worksteps are completed.
     * @protected
     */
    protected get stepsCompleted(): boolean {
        return this.workstepService.isCompleted;
    }
}
