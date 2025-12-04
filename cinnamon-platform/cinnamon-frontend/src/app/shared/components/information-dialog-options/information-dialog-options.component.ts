import { Component } from '@angular/core';
import {
    InformationDialogPartComponent
} from "@shared/components/information-dialog-part/information-dialog-part.component";

/**
 * Options section of the information dialog.
 * Can be used to explain the available options for an input.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'info-options',
    standalone: false,
    templateUrl: '../information-dialog-part/information-dialog-part.component.html',
    styleUrl: '../information-dialog-part/information-dialog-part.component.less'
})
export class InformationDialogOptionsComponent extends InformationDialogPartComponent {
    protected override getTitle(): string {
        return "Options";
    }
}
