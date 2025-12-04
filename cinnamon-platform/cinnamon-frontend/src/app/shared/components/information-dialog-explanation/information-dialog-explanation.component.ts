import { Component } from '@angular/core';
import {
    InformationDialogPartComponent
} from "@shared/components/information-dialog-part/information-dialog-part.component";

/**
 * Explanation section of the information dialog.
 * Can be used to provide an explanation for an input.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'info-explanation',
    standalone: false,
    templateUrl: '../information-dialog-part/information-dialog-part.component.html',
    styleUrl: '../information-dialog-part/information-dialog-part.component.less'
})
export class InformationDialogExplanationComponent extends InformationDialogPartComponent {
    protected override getTitle(): string {
        return "Explanation";
    }
}
