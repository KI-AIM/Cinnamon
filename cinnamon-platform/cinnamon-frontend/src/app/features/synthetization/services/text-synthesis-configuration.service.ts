import { Injectable } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, ValidatorFn, Validators } from "@angular/forms";
import { ConfigurationObject } from "@shared/model/anonymization-attribute-config";
import { AlgorithmDefinition } from "@shared/model/algorithm-definition";
import { ConfigurationGroupDefinition } from "@shared/model/configuration-group-definition";
import { ConfigurationInputDefinition } from "@shared/model/configuration-input-definition";
import { ConfigurationInputType } from "@shared/model/configuration-input-type";
import { DataConfiguration } from "@shared/model/data-configuration";

@Injectable({
    providedIn: 'root',
})
export class TextSynthesisConfigurationService {
    public readonly formGroupName = "text_synthesis_configuration";

    constructor(
        private readonly formBuilder: FormBuilder,
    ) {
    }

    public initForm(form: FormGroup, config: ConfigurationObject | null, disabled: boolean): void {
        const group = this.createGroup(config as any, disabled);
        form.addControl(this.formGroupName, group);
    }

    public syncFormWithDefinition(
        form: FormGroup,
        definition: AlgorithmDefinition,
        dataConfiguration: DataConfiguration,
        disabled: boolean,
    ): void {
        const algorithmGroup = form.get(
            `${this.formGroupName}.synthetization_configuration.algorithm`,
        ) as FormGroup | null;
        if (algorithmGroup == null) {
            return;
        }

        const currentValues = algorithmGroup.getRawValue();
        this.syncGroupFromDefinition(
            algorithmGroup,
            definition,
            currentValues,
            dataConfiguration,
            disabled,
            new Set(["synthesizer"]),
        );
    }

    public createGroup(config: any, disabled: boolean): FormGroup {
        const algorithm = config?.synthetization_configuration?.algorithm ?? {};

        return this.formBuilder.group({
            synthetization_configuration: this.formBuilder.group({
                algorithm: this.formBuilder.group({
                    synthesizer: new FormControl({value: algorithm.synthesizer ?? "", disabled}, [Validators.required]),
                }),
            }),
        });
    }

    private syncGroupFromDefinition(
        targetGroup: FormGroup,
        definition: ConfigurationGroupDefinition,
        initialValues: any,
        dataConfiguration: DataConfiguration,
        disabled: boolean,
        preservedControls: Set<string> = new Set(),
    ): void {
        const desiredControls = new Set<string>(preservedControls);

        for (const inputDefinition of definition.parameters ?? []) {
            desiredControls.add(inputDefinition.name);
            targetGroup.setControl(
                inputDefinition.name,
                this.createControl(inputDefinition, initialValues?.[inputDefinition.name], dataConfiguration, disabled),
            );
        }

        for (const [name, groupDefinition] of Object.entries(definition.configurations ?? {})) {
            desiredControls.add(name);
            targetGroup.setControl(
                name,
                this.createDefinitionGroup(groupDefinition, initialValues?.[name] ?? {}, dataConfiguration, disabled),
            );
        }

        for (const [name, groupDefinition] of Object.entries(definition.options ?? {})) {
            desiredControls.add(name);
            targetGroup.setControl(
                name,
                this.createDefinitionGroup(groupDefinition, initialValues?.[name] ?? {}, dataConfiguration, disabled),
            );
        }

        for (const controlName of Object.keys(targetGroup.controls)) {
            if (!desiredControls.has(controlName)) {
                targetGroup.removeControl(controlName);
            }
        }
    }

    private createDefinitionGroup(
        groupDefinition: ConfigurationGroupDefinition,
        initialValues: any,
        dataConfiguration: DataConfiguration,
        disabled: boolean,
    ): FormGroup {
        const group: Record<string, FormControl | FormArray | FormGroup> = {};

        for (const inputDefinition of groupDefinition.parameters ?? []) {
            group[inputDefinition.name] = this.createControl(
                inputDefinition,
                initialValues?.[inputDefinition.name],
                dataConfiguration,
                disabled,
            );
        }

        for (const [name, childDefinition] of Object.entries(groupDefinition.configurations ?? {})) {
            group[name] = this.createDefinitionGroup(
                childDefinition,
                initialValues?.[name] ?? {},
                dataConfiguration,
                disabled,
            );
        }

        for (const [name, childDefinition] of Object.entries(groupDefinition.options ?? {})) {
            group[name] = this.createDefinitionGroup(
                childDefinition,
                initialValues?.[name] ?? {},
                dataConfiguration,
                disabled,
            );
        }

        return new FormGroup(group);
    }

    private createControl(
        inputDefinition: ConfigurationInputDefinition,
        initialValue: any,
        dataConfiguration: DataConfiguration,
        disabled: boolean,
    ): FormControl | FormArray {
        const mandatory = this.isMandatory(inputDefinition);
        const resolvedInitialValue = initialValue ?? inputDefinition.default_value;

        if (inputDefinition.type === ConfigurationInputType.LIST) {
            const controls = [];
            for (const value of (resolvedInitialValue ?? []) as number[]) {
                controls.push(new FormControl({value, disabled}, Validators.required));
            }
            return new FormArray(controls, mandatory ? Validators.required : null);
        }

        if (inputDefinition.type === ConfigurationInputType.ATTRIBUTE_LIST) {
            const selectedValues = Array.isArray(resolvedInitialValue) ? resolvedInitialValue : [];
            return new FormArray(
                selectedValues.map(value => new FormControl({value, disabled})),
                mandatory ? Validators.required : null,
            );
        }

        const validators: ValidatorFn[] = [];
        if (mandatory && inputDefinition.type !== ConfigurationInputType.BOOLEAN) {
            validators.push(Validators.required);
        }
        if (inputDefinition.min_value !== null) {
            validators.push(Validators.min(inputDefinition.min_value));
        }
        if (inputDefinition.max_value !== null) {
            validators.push(Validators.max(inputDefinition.max_value));
        }

        const value = resolvedInitialValue ?? this.getDefaultValueForInput(inputDefinition, dataConfiguration);
        return new FormControl({value, disabled}, validators);
    }

    private getDefaultValueForInput(
        inputDefinition: ConfigurationInputDefinition,
        dataConfiguration: DataConfiguration,
    ): any {
        if (inputDefinition.type === ConfigurationInputType.ATTRIBUTE_LIST) {
            return dataConfiguration.configurations.map(configuration => configuration.name);
        }
        return inputDefinition.default_value;
    }

    private isMandatory(inputDefinition: { mandatory?: boolean | null }): boolean {
        return inputDefinition.mandatory !== false;
    }
}
