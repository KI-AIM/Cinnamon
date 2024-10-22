import { Component, Input, TemplateRef } from '@angular/core';
import { ConfigurationInputDefinition } from "../../model/configuration-input-definition";
import { MatDialog } from "@angular/material/dialog";
import { FormArray, FormControl, FormGroup, Validators } from "@angular/forms";

@Component({
  selector: 'app-configuration-input-array',
  templateUrl: './configuration-input-array.component.html',
  styleUrls: ['./configuration-input-array.component.less']
})
export class ConfigurationInputArrayComponent {
    @Input() configurationInputDefinition!: ConfigurationInputDefinition;
    @Input() parentForm!: FormGroup;
    @Input() disabled!: boolean;

    constructor(public dialog: MatDialog) {
    }

    openDialog(templateRef: TemplateRef<any>) {
        this.dialog.open(templateRef, {
            width: '60%'
        });
    }

    get formArray(): FormArray {
        return this.parentForm.controls[this.configurationInputDefinition.name] as FormArray;
    }

    addValue() {
        this.formArray.push(new FormControl(0, Validators.required));
    }

    removeValue(index: number) {
        this.formArray.removeAt(index);
    }
}
