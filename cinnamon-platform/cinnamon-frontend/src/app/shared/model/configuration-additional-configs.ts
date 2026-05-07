import { FormGroup } from "@angular/forms";
import { Algorithm } from "./algorithm";
import { DataConfiguration } from "./data-configuration";

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
    applicableAlgorithmNames: string[] | null;
    insertAfterGroupName: string | null;
    initializeForm: (formGroup: FormGroup, configs: any, disabled: boolean) => void;
    predicate: ((algorithm: Algorithm, dataConfiguration: DataConfiguration) => boolean) | null;

    constructor(
        component: any,
        title: string,
        description: string,
        formGroupName: string,
        initializeForm: (formGroup: FormGroup, configs: any, disabled: boolean) => void,
        applicableAlgorithmNames: string[] | null = null,
        insertAfterGroupName: string | null = null,
        predicate: ((algorithm: Algorithm, dataConfiguration: DataConfiguration) => boolean) | null = null,
    ) {
        this.component = component;
        this.title = title;
        this.description = description;
        this.formGroupName = formGroupName;
        this.applicableAlgorithmNames = applicableAlgorithmNames;
        this.insertAfterGroupName = insertAfterGroupName;
        this.initializeForm = initializeForm;
        this.predicate = predicate;
    }
}
