import { Component, Input, TemplateRef } from '@angular/core';
import { ConfigurationInputDefinition } from "../../model/configuration-input-definition";
import { MatDialog } from "@angular/material/dialog";
import { ConfigurationInputType } from "../../model/configuration-input-type";

@Component({
  selector: 'app-configuration-input-info',
  templateUrl: './configuration-input-info.component.html',
  styleUrls: ['./configuration-input-info.component.less']
})
export class ConfigurationInputInfoComponent {
    protected readonly ConfigurationInputType = ConfigurationInputType;

    @Input() configurationInputDefinition: ConfigurationInputDefinition;

    constructor(public dialog: MatDialog) {
    }

    openDialog(templateRef: TemplateRef<any>) {
        this.dialog.open(templateRef, {
            width: '60%'
        });
    }
}
