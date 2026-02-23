import { Component } from '@angular/core';
import {
    InformationDialogPartComponent
} from "@shared/components/information-dialog-part/information-dialog-part.component";

/**
 * Examples section of the information dialog.
 * Can be used to provide example values for an input.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'info-examples',
    standalone: false,
    templateUrl: '../information-dialog-part/information-dialog-part.component.html',
    styleUrl: '../information-dialog-part/information-dialog-part.component.less'
})
export class InformationDialogExamplesComponent extends InformationDialogPartComponent {
    protected override getTitle(): string {
        return "Examples";
    }
}
