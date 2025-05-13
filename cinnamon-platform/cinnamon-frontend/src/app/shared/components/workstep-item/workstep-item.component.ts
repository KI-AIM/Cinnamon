import { booleanAttribute, Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Steps } from "@core/enums/steps";
import { StatusService } from "@shared/services/status.service";
import { WorkstepService } from "@shared/services/workstep.service";

@Component({
  selector: 'app-workstep-item',
  standalone: false,
  templateUrl: './workstep-item.component.html',
  styleUrl: './workstep-item.component.less'
})
export class WorkstepItemComponent implements OnInit {

    @Input() public stepIndex!: number;
    @Input({transform: booleanAttribute}) public invalid: boolean = false;
    @Input({transform: booleanAttribute}) public locked: boolean = false;
    @Input() public title!: string;

    @Input() public altConfirm: string | null = null;
    @Input() public altConfirmValid: boolean = true;
    @Input() public altConfirmAll: boolean = false;

    @Output() public confirm = new EventEmitter<void>();
    @Output() public confirmAlt = new EventEmitter<void>();

    public constructor(
        private readonly statusService: StatusService,
        private readonly workstepService: WorkstepService,
    ) {
    }

    public ngOnInit(): void {
        this.locked = this.statusService.isStepCompleted(Steps.VALIDATION);
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
