import { FormGroup } from "@angular/forms";
import { AnonymizationAttributeRowConfiguration } from "@shared/model/anonymization-attribute-config";

export class ConfigurationAdditionalConfigs {
    configs: AdditionalConfig[];

    constructor(configs: AdditionalConfig[]) {
        this.configs = configs;
    }
}

export class AdditionalConfig {
    component: any;
    title: string;
    description: string;
    formGroupName: string;
    initializeForm: (formGroup: FormGroup, configs: AnonymizationAttributeRowConfiguration[] | null, disabled: boolean) => void;

    constructor(component: any, title: string, description: string, formGroupName: string, initializeForm: (formGroup: FormGroup, configs: AnonymizationAttributeRowConfiguration[] | null, disabled: boolean) => void) {
        this.component = component;
        this.title = title;
        this.description = description;
        this.formGroupName = formGroupName;
        this.initializeForm = initializeForm;
    }
}
