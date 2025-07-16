import { FormGroup } from "@angular/forms";

export interface AdditionalConfigurationGroup {
    disabled: boolean;
    form: FormGroup;

    patchValue(configs: any): void;
}
