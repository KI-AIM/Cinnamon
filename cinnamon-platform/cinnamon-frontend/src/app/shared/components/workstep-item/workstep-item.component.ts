import { booleanAttribute, Component, ElementRef, EventEmitter, Input, Output, ViewChild, } from '@angular/core';
import { MatExpansionPanel } from "@angular/material/expansion";
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
    @Input() public title!: string;

    @Input() public altConfirm: string | null = null;
    @Input() public altConfirmValid: boolean = true;
    @Input() public altConfirmAll: boolean = false;

    @Output() public confirm = new EventEmitter<void>();
    @Output() public confirmAlt = new EventEmitter<void>();

    /**
     * The MatExpansionPanel
     * @private
     */
    @ViewChild(MatExpansionPanel, {read: ElementRef}) private panel: ElementRef;

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

    /**
     * Temporarily hides the overflow for mat-expansion panels.
     * And shows the content of the panel if it is currently expanded.
     *
     * @protected
     */
    protected beforeCollapseExpand() {
        this.panel.nativeElement.classList.add('cinnamon-overflow-hidden');
        if (this.panel.nativeElement.classList.contains('mat-expanded')) {
            this.panel.nativeElement.querySelector('.mat-expansion-panel-body').style.display = '';
        }
    }

    /**
     * Show the overflow for mat-expansion panels and hides the content of the panel.
     *
     * @protected
     */
    protected afterCollapse() {
        this.panel.nativeElement.classList.remove('cinnamon-overflow-hidden');
        this.panel.nativeElement.querySelector('.mat-expansion-panel-body').style.display = 'none';
    }

    /**
     * Shows the overflow for mat-expansion panels.
     *
     * @protected
     */
    protected afterExpand() {
        this.panel.nativeElement.classList.remove('cinnamon-overflow-hidden');
    }
}
