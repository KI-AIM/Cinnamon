import { FormGroup } from "@angular/forms";

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
    initializeForm: (formGroup: FormGroup) => void;

    constructor(component: any, title: string, description: string, formGroupName: string, initializeForm: (formGroup: FormGroup) => void) {
        this.component = component;
        this.title = title;
        this.description = description;
        this.formGroupName = formGroupName;
        this.initializeForm = initializeForm;
    }
}
