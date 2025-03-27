import { Component, Input, TemplateRef } from '@angular/core';
import { ConfigurationInputDefinition } from "../../model/configuration-input-definition";
import { MatDialog } from "@angular/material/dialog";
import { ConfigurationInputType } from "../../model/configuration-input-type";

/**
 * Component providing the information popup for an input and the corresponding button.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'app-configuration-input-info',
    templateUrl: './configuration-input-info.component.html',
    styleUrls: ['./configuration-input-info.component.less'],
    standalone: false
})
export class ConfigurationInputInfoComponent {
    protected readonly ConfigurationInputType = ConfigurationInputType;

    /**
     * The definition the displayed information is based on.
     */
    @Input() configurationInputDefinition: ConfigurationInputDefinition;

    constructor(public dialog: MatDialog) {
    }

    /**
     * Opens the info popup.
     * @param templateRef The reference to the popup.
     */
    openDialog(templateRef: TemplateRef<any>) {
        this.dialog.open(templateRef, {
            width: '60%'
        });
    }
}
