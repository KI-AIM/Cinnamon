import { Component, ElementRef, Input, TemplateRef, ViewChild } from "@angular/core";
import {
	ConfigurationType,
	getConfigurationForConfigurationType,
	getConfigurationTypeForString,
	getConfigurationTypeStringForIndex,
	getConfigurationsForDatatype,
} from "src/app/shared/model/configuration-types";
import { List } from "src/app/core/utils/list";
import { DateformatComponent } from "../configurationSettings/dateformat/dateformat.component";
import { DatetimeformatComponent } from "../configurationSettings/datetimeformat/datetimeformat.component";
import { StringpatternComponent } from "../configurationSettings/stringpattern/stringpattern.component";
import { MatDialog } from "@angular/material/dialog";
import { RangeComponent } from "../configurationSettings/range/range.component";
import { FormArray, FormBuilder, FormGroup, Validators } from "@angular/forms";
import { DataType, dataTypeFromString } from "../../../../shared/model/data-type";

@Component({
	selector: "app-additional-configuration",
	templateUrl: "./additional-configuration.component.html",
	styleUrls: ["./additional-configuration.component.less"],
})
export class AdditionalConfigurationComponent {
	ConfigurationType = ConfigurationType;

	@Input() attrNumber: Number;
    @Input() disabled: boolean = false;
    @Input() form!: FormGroup;

	@ViewChild("dateFormat") dateFormatComponent: DateformatComponent;
	@ViewChild("dateTimeFormat") dateTimeFormatComponent: DatetimeformatComponent;
	@ViewChild("range") rangeComponent: RangeComponent;
	@ViewChild("stringPattern") stringPatternComponent: StringpatternComponent;

	currentConfigurationSelection: ConfigurationType | undefined;

    selected = "standardSelection";

    constructor(
        public dialog: MatDialog,
        private readonly formBuilder: FormBuilder,
    ) {

    }

    protected getType(): DataType {
        return dataTypeFromString(this.form.controls['type'].value);
    }

    protected getConfigurations(): FormArray<FormGroup> {
       return this.form.controls['configurations'] as FormArray<FormGroup>;
    }

    protected getConfigurationGroups(): FormGroup[] {
        return (this.form.controls['configurations'] as FormArray<FormGroup>).controls;
    }

    openDialog(templateRef: TemplateRef<any>) {
        this.dialog.open(templateRef, {
            width: '60%'
        });
    }

	changeConfigurationSelection(event: any) {
        this.currentConfigurationSelection = event.value;
        this.addConfiguration();
	}

    areConfigurationAvailable(type: DataType): boolean {
        return this.getConfigurationsForDatatype(type).size() > 0;
    }

	getConfigurationsForDatatype(type: DataType): List<ConfigurationType> {
        var result = new List<ConfigurationType>
        var configurationTypes = getConfigurationsForDatatype(type);

        configurationTypes.getAll().forEach(configurationType => {
            if (!this.isConfigurationAlreadyAdded(configurationType)) {
                result.add(configurationType);
            }
        });

		return result;
	}

    isConfigurationAlreadyAdded(configurationType: ConfigurationType): boolean {
        for (const group of Object.values(this.getConfigurations().controls)) {
            const name = group.controls['name'].value;
            if (name === getConfigurationForConfigurationType(configurationType)) {
                return true;
            }
        }

        return false;
    }

	getConfigurationTypeForIndex(index: number): String {
		return getConfigurationTypeStringForIndex(index);
	}

	addConfiguration() {
        if (this.currentConfigurationSelection != undefined) {
            switch (
                getConfigurationTypeForString(
                    ConfigurationType[this.currentConfigurationSelection]
                )
            ) {
                case ConfigurationType.DATEFORMAT: {
                    this.getConfigurations().push(
                      this.formBuilder.group({
                          name: ["DateFormatConfiguration"],
                          dateFormatter: ["", {validators: [Validators.required]}],
                      })
                    );

                    this.currentConfigurationSelection = undefined;
                    this.selected = "standardSelection";
                    break;
                }
                case ConfigurationType.DATETIMEFORMAT: {
                    this.getConfigurations().push(
                        this.formBuilder.group({
                            name: ["DateTimeFormatConfiguration"],
                            dateTimeFormatter: ["", {validators: [Validators.required]}],
                        })
                    );
                    this.currentConfigurationSelection = undefined;
                    this.selected = "standardSelection";
                    break;
                }
                case ConfigurationType.RANGE: {
                    this.getConfigurations().push(
                        this.formBuilder.group({
                            name: ["RangeConfiguration"],
                            minValue: ["", {validators: [Validators.required]}],
                            maxValue: ["", {validators: [Validators.required]}],
                        })
                    );
                    this.currentConfigurationSelection = undefined;
                    this.selected = "standardSelection";
                    break;
                }
                case ConfigurationType.STRINGPATTERN: {
                    this.getConfigurations().push(
                        this.formBuilder.group({
                            name: ["StringPatternConfiguration"],
                            pattern: ["", {validators: [Validators.required]}],
                        })
                    );
                    this.currentConfigurationSelection = undefined;
                    this.selected = "standardSelection";
                    break;
                }
            }
        }
	}

    protected removeConfiguration(index: number) {
        this.getConfigurations().removeAt(index);
    }
}
