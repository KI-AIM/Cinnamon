import { Component, Input } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from "@angular/forms";
import { ConfigurationInputDefinition } from "../../model/configuration-input-definition";

@Component({
    selector: 'app-configuration-input-named-list',
    templateUrl: './configuration-input-named-list.component.html',
    styleUrls: ['./configuration-input-named-list.component.less'],
    standalone: false
})
export class ConfigurationInputNamedListComponent {
    @Input() configurationInputDefinition!: ConfigurationInputDefinition;
    @Input() parentForm!: FormGroup;
    @Input() disabled!: boolean;

    get formArray(): FormArray {
        return this.parentForm.controls[this.configurationInputDefinition.name] as FormArray;
    }

    hasValues(): boolean {
        return this.formArray.length > 0;
    }

    addValue() {
        this.formArray.push(this.createItemGroup());
    }

    removeAllValues() {
        this.formArray.clear();
    }

    removeValue(index: number) {
        this.formArray.removeAt(index);
    }

    private createItemGroup(item?: {name?: string, description?: string}): FormGroup {
        return new FormGroup({
            name: new FormControl(item?.name ?? "", Validators.required),
            description: new FormControl(item?.description ?? ""),
        });
    }
}
