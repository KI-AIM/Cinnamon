import { booleanAttribute, Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { Steps } from "@core/enums/steps";
import { StatusService } from "@shared/services/status.service";
import { WorkstepService } from "@shared/services/workstep.service";

@Component({
    selector: 'app-workstep-list',
    standalone: false,
    templateUrl: './workstep-list.component.html',
    styleUrl: './workstep-list.component.less',
})
export class WorkstepListComponent implements OnInit, OnDestroy {

    @Input() public confirmLabel!: string;
    @Input({transform: booleanAttribute}) public invalid: boolean = false;
    @Input({transform: booleanAttribute}) public locked: boolean = false;
    @Input() public numberSteps!: number;
    @Input({transform: booleanAttribute}) public resetSteps: boolean = false
    @Input() public step!: Steps;

    @Output() public confirm = new EventEmitter<void>();

    public constructor(
        private readonly statusService: StatusService,
        private readonly workstepService: WorkstepService,
    ) {
    }

    public ngOnInit() {
        this.workstepService.init(this.step, this.numberSteps, this.statusService.isStepCompleted(this.step), this.resetSteps);
    }

    public ngOnDestroy() {
        this.workstepService.shutdown();
    }

    /**
     * Checks if all worksteps are completed.
     * @protected
     */
    protected get stepsCompleted(): boolean {
        return this.workstepService.isCompleted;
    }
}
