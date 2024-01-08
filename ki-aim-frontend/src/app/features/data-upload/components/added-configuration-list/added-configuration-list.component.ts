import { Component, Input, TemplateRef } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { instanceToPlain } from "class-transformer";
import { ColumnConfiguration } from "src/app/shared/model/column-configuration";
import { Configuration } from "src/app/shared/model/configuration";

@Component({
	selector: "app-added-configuration-list",
	templateUrl: "./added-configuration-list.component.html",
	styleUrls: ["./added-configuration-list.component.less"],
})
export class AddedConfigurationListComponent {
	@Input() column: ColumnConfiguration;

    instanceToPlain = instanceToPlain;

    constructor(public dialog: MatDialog) {

    }

    configAsString(config: any): String {
        return JSON.stringify(this.instanceToPlain(config));
    }

	removeConfiguration(configuration: Configuration) {
        this.column.removeConfiguration(configuration);
    }

    hasConfiguration(): boolean {
        return this.column.configurations.length > 0; 
    }

    showRemovalDialog(templateRef: TemplateRef<any>) {
        this.dialog.open(templateRef, {
            width: '55%'
        }); 
    }
}
