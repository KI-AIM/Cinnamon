import { Component, ElementRef, Input, ViewChild } from "@angular/core";
import {
	ConfigurationType,
	getConfigurationForConfigurationType,
	getConfigurationTypeForString,
	getConfigurationTypeStringForIndex,
	getConfigurationsForDatatype,
} from "src/app/shared/model/configuration-types";
import { ColumnConfiguration } from "src/app/shared/model/column-configuration";
import { List } from "src/app/core/utils/list";
import { DateformatComponent } from "../configurationSettings/dateformat/dateformat.component";
import { DatetimeformatComponent } from "../configurationSettings/datetimeformat/datetimeformat.component";
import { StringpatternComponent } from "../configurationSettings/stringpattern/stringpattern.component";

@Component({
	selector: "app-additional-configuration",
	templateUrl: "./additional-configuration.component.html",
	styleUrls: ["./additional-configuration.component.less"],
})
export class AdditionalConfigurationComponent {
	ConfigurationType = ConfigurationType;

	@Input() attrNumber: Number;
	@Input() column: ColumnConfiguration;

	@ViewChild("dateFormat") dateFormatComponent: DateformatComponent;
	@ViewChild("dateTimeFormat")
	dateTimeFormatComponent: DatetimeformatComponent;
	@ViewChild("stringPattern") stringPatternComponent: StringpatternComponent;

	currentConfigurationSelection: ConfigurationType | undefined;

	changeConfigurationSelection(event: any) {
		this.currentConfigurationSelection = event.target.value;
	}

    areConfigurationAvailable(type: String): boolean {
        return this.getConfigurationsForDatatype(type).size() > 0;  
    }

	getConfigurationsForDatatype(type: String): List<ConfigurationType> {
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
        var result = false; 
        this.column.configurations.forEach(configuration => {
            if (configuration.constructor.name == getConfigurationForConfigurationType(configurationType)) {
                result = true; 
            }
        });

        return result; 
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
                    this.column.addConfiguration(
                        this.dateFormatComponent.getDateFormatConfiguration()
                    );
                    this.currentConfigurationSelection = undefined;
                    break;
                }
                case ConfigurationType.DATETIMEFORMAT: {
                    this.column.addConfiguration(
                        this.dateTimeFormatComponent.getDateTimeFormatConfiguration()
                    );
                    this.currentConfigurationSelection = undefined;
                    break;
                }
                case ConfigurationType.STRINGPATTERN: {
                    this.column.addConfiguration(
                        this.stringPatternComponent.getStringPatternConfiguration()
                    );
                    this.currentConfigurationSelection = undefined;
                    break;
                }
            }
        }
	}
}
