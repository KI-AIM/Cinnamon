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
     * Title of the workstep.
     */
    @Input() public header!: string;

    /**
     * If the workstep is invalid.
     */
    @Input({transform: booleanAttribute}) public invalid: boolean = false;

    /**
     * If the box can be closed/opened.
     */
    @Input({transform: booleanAttribute}) public toggleable: boolean = false;
}
