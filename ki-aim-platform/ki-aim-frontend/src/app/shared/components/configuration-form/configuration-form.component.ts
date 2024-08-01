import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from "@angular/forms";
import { stringify } from "yaml";
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

    @Input() public algorithm: Algorithm;
    @Input() public algorithmName: string;
    protected algorithmDefinition: AlgorithmDefinition;

    @Output() public submitConfiguration = new EventEmitter<string>();
    protected form!: FormGroup;

    constructor(
        private readonly anonService: AlgorithmService
    ) {
        // this.anonService.getAlgorithmDefinition(this.algorithm).subscribe(value => this.algorithmDefinition = value);
    }

    ngOnInit() {

        console.log(this.algorithmName);
        console.log(this.algorithm);
        this.anonService.getAlgorithmDefinitionByName(this.algorithmName)
            .subscribe(value => {
                this.algorithmDefinition = value


                const formGroup: any = {};

                Object.entries(this.algorithmDefinition.arguments).forEach(([name, groupDefinition]) => {
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

                this.form = new FormGroup(formGroup);


            });


    }

    ngAfterViewInit() {
        console.log('hi');
    }

    getConfiguration() {
        return this.form.getRawValue();
    }

    setConfiguration(configuration: Object) {
        this.form.setValue(configuration);
    }

    onSubmit() {
        this.submitConfiguration.emit(stringify(this.form.getRawValue()));
    }

    protected readonly Object = Object;
}
