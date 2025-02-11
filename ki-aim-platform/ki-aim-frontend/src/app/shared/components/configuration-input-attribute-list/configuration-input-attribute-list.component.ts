import { Component, Input, OnInit } from '@angular/core';
import { Observable, tap } from "rxjs";
import { DataConfiguration } from "../../model/data-configuration";
import { DataConfigurationService } from "../../services/data-configuration.service";
import { FormArray, FormControl, FormGroup } from "@angular/forms";
import { ConfigurationInputDefinition } from "../../model/configuration-input-definition";

@Component({
  selector: 'app-configuration-input-attribute-list',
  templateUrl: './configuration-input-attribute-list.component.html',
  styleUrls: ['./configuration-input-attribute-list.component.less']
})
export class ConfigurationInputAttributeListComponent implements OnInit {
    @Input() public configurationInputDefinition!: ConfigurationInputDefinition;
    @Input() public parentForm!: FormGroup;
    @Input() public disabled!: boolean;

    protected dataConfiguration$: Observable<DataConfiguration>;

    constructor(private readonly dataConfigService: DataConfigurationService) {
    }

    public ngOnInit() {
        this.dataConfiguration$ = this.dataConfigService.dataConfiguration$.pipe(
            tap(dataConfiguration => {
                this.initInverted(dataConfiguration);
            })
        );
    }

    protected get formArray(): FormArray {
        return this.parentForm.controls[this.configurationInputDefinition.name] as FormArray;
    }

    protected get formArrayInverted(): FormArray | null {
        if (!this.configurationInputDefinition.invert) {
            return null;
        }
        return this.parentForm.controls[this.configurationInputDefinition.invert] as FormArray;
    }

    protected toggleCheckbox(attributeName: string, checked: boolean) {
        const formArray = this.formArray;
        const formArrayInverted = this.formArrayInverted;

        if (checked) {
            formArray.push(new FormControl(attributeName));

            if (formArrayInverted !== null) {
                const index = formArrayInverted.controls.findIndex(control => control.value === attributeName);
                if (index !== -1) {
                    formArrayInverted.removeAt(index);
                }
            }
        } else {
            const index = formArray.controls.findIndex(control => control.value === attributeName);
            if (index !== -1) {
                formArray.removeAt(index);
            }

            if (formArrayInverted !== null) {
                formArrayInverted.push(new FormControl(attributeName));
            }
        }
    }

    /**
     * Fills the array of not selected columns with all attributes.
     * @param dataConfiguration The data configuration.
     * @private
     */
    private initInverted(dataConfiguration: DataConfiguration) {
        const formArrayInverted = this.formArrayInverted;

        if (formArrayInverted === null) {
            return;
        }

        for (const attribute of dataConfiguration.configurations) {
            formArrayInverted.push(new FormControl(attribute.name));
        }
    }
}
