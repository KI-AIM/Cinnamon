import { booleanAttribute, Component, Input } from '@angular/core';

/**
 * Component for wrapping a workstep.
 * Uses a mat-expansion-panel internally.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'app-workstep-box',
    templateUrl: './workstep-box.component.html',
    styleUrl: './workstep-box.component.less',
    standalone: false,
})
export class WorkstepBoxComponent {
    /**
     * If the workstep is invalid.
     */
    @Input({transform: booleanAttribute}) public invalid: boolean = false;

    /**
     * Title of the workstep.
     */
    @Input() public title!: string;

    /**
     * If the box can be closed/opened.
     */
    @Input({transform: booleanAttribute}) public toggleable: boolean = false;
}
