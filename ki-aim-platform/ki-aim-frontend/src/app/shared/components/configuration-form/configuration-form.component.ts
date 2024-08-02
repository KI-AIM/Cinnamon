import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from "@angular/forms";
import { ConfigurationInputType } from "../../model/configuration-input-type";
import { AlgorithmDefinition } from "../../model/algorithm-definition";
import { AlgorithmService } from "../../services/algorithm.service";
import { Algorithm } from "../../model/algorithm";

@Component({
    selector: 'app-configuration-form',
    templateUrl: './configuration-form.component.html',
    styleUrls: ['./configuration-form.component.less']
})
export class ConfigurationFormComponent implements OnInit {

    @Input() public algorithm!: Algorithm;
    @Input() public algorithmName!: string;
    @Input() public disabled!: boolean;
    protected algorithmDefinition: AlgorithmDefinition;

    @Output() public submitConfiguration = new EventEmitter<string>();
    protected form: FormGroup;

    constructor(
        private readonly anonService: AlgorithmService
    ) {
       this.form = new FormGroup({});
    }

    ngOnInit() {
        this.anonService.getAlgorithmDefinitionByName(this.algorithmName)
            .subscribe(value => {
                this.algorithmDefinition = value
                this.form = this.createForm(value);
                this.updateForm();
            });
    }

    public get valid(): boolean {
        return !this.form.invalid;
    }

    public get formData(): Object {
        return this.form.getRawValue();
    }

    getConfiguration() {
        return this.form.getRawValue();
    }

    setConfiguration(configuration: Object) {
        this.form.setValue(configuration);
    }

    getGroupNames(): Array<string> {
        if (this.algorithmDefinition == undefined) {
            return [];
        }

        return Object.keys(this.algorithmDefinition.arguments);
    }

    private updateForm() {
        if (this.disabled) {
            this.form.disable();
        } else {
            this.form.enable();
        }
    }

    onSubmit() {
        this.submitConfiguration.emit(this.form.getRawValue());
    }

    private createForm(algorithmDefinition: AlgorithmDefinition): FormGroup {
        const formGroup: any = {};

        Object.entries(algorithmDefinition.arguments).forEach(([name, groupDefinition]) => {
            const group: any = {};

            groupDefinition.parameters.forEach(inputDefinition => {
                if (inputDefinition.type === ConfigurationInputType.ARRAY) {
                    const controls = [];
                    for (const defaultValue of inputDefinition.default_value as number[]) {
                        controls.push(new FormControl(defaultValue, Validators.required));
                    }

                    group[inputDefinition.name] = new FormArray(controls, Validators.required)
                } else {
                    const validators = [Validators.required];
                    if (inputDefinition.min_value !== null) {
                        validators.push(Validators.min(inputDefinition.min_value));
                    }
                    if (inputDefinition.max_value !== null) {
                        validators.push(Validators.max(inputDefinition.max_value));
                    }

                    group[inputDefinition.name] = new FormControl(inputDefinition.default_value, validators)
                }
            });

            formGroup[name] = new FormGroup(group);
        });

        return new FormGroup(formGroup);
    }
}
