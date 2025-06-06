import { Component, Input, TemplateRef } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { instanceToPlain } from "class-transformer";
import { ColumnConfiguration } from "src/app/shared/model/column-configuration";
import { Configuration } from "src/app/shared/model/configuration";
import { FormArray, FormGroup } from "@angular/forms";

@Component({
    selector: "app-added-configuration-list",
    templateUrl: "./added-configuration-list.component.html",
    styleUrls: ["./added-configuration-list.component.less"],
    standalone: false
})
export class AddedConfigurationListComponent {
    @Input() disabled: boolean = false;
    @Input() form!: FormGroup;

    instanceToPlain = instanceToPlain;

    constructor(public dialog: MatDialog) {

    }

    configAsString(index: string): String {
        const group = this.getConfigurations().controls[parseInt(index)] as FormGroup;
        return JSON.stringify(group.getRawValue());
    }

	removeConfiguration(index: string) {
        this.getConfigurations().removeAt(parseInt(index));
    }

    hasConfiguration(): boolean {
        return this.getConfigurations().length > 0;
    }

    showRemovalDialog(templateRef: TemplateRef<any>) {
        this.dialog.open(templateRef, {
            width: '55%'
        });
    }

    protected getConfigurations(): FormArray {
        return this.form.controls['configurations'] as FormArray;
    }
}
