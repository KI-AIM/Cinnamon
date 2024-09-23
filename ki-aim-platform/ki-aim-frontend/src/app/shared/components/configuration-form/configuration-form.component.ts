import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from "@angular/forms";
import { ConfigurationInputType } from "../../model/configuration-input-type";
import { AlgorithmDefinition } from "../../model/algorithm-definition";
import { AlgorithmService } from "../../services/algorithm.service";
import { Algorithm } from "../../model/algorithm";

/**
 * HTML form and submit button for one algorithm.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'app-configuration-form',
    templateUrl: './configuration-form.component.html',
    styleUrls: ['./configuration-form.component.less']
})
export class ConfigurationFormComponent implements OnInit {

    /**
     * The algorithm that is configured with this form.
     */
    @Input() public algorithm!: Algorithm;

    /**
     * If this form is disabled.
     */
    @Input() public disabled!: boolean;

    /**
     * The definition of the configuration fetched from the external API.
     * @protected
     */
    protected algorithmDefinition: AlgorithmDefinition;
    /**
     * Dynamically created form for the configuration.
     * @protected
     */
    protected form: FormGroup;

    /**
     * Event that gets triggered on every change.
     */
    @Output() public onChange: EventEmitter<void> = new EventEmitter();

    /**
     * Event for submitting the configurations.
     * Emits the raw JSON of the form.
     */
    @Output() public submitConfiguration = new EventEmitter<Object>();

    constructor(
        private readonly anonService: AlgorithmService
    ) {
       this.form = new FormGroup({});
    }

    ngOnInit() {
        // Fetch the configuration definition.
        this.anonService.getAlgorithmDefinition(this.algorithm)
            .subscribe(value => {
                this.algorithmDefinition = value
                this.form = this.createForm(value);
                this.updateForm();

                this.form.valueChanges.subscribe(value => {
                    this.onChange.emit();
                });
            });
    }

    /**
     * Returns if the form and all its inputs are valid.
     */
    public get valid(): boolean {
        return !this.form.invalid;
    }

    /**
     * Gets the raw JSON of the form.
     */
    public get formData(): Object {
        return this.form.getRawValue();
    }

    /**
     * Sets the values of the form from the given JSON object.
     * @param configuration JSON of the form.
     */
    public setConfiguration(configuration: Object) {
        this.form.setValue(configuration);
    }

    /**
     * Returns the names of the groups in the configuration definition.
     * @protected
     */
    protected getGroupNames(): Array<string> {
        if (this.algorithmDefinition == undefined) {
            return [];
        }

        return Object.keys(this.algorithmDefinition.configurations);
    }

    /**
     * Enables or disables the form based on the current value of {@link disabled}.
     * @private
     */
    private updateForm() {
        if (this.disabled) {
            this.form.disable();
        } else {
            this.form.enable();
        }
    }

    /**
     * Emits the submit-event with the current form.
     * @protected
     */
    protected onSubmit() {
        this.submitConfiguration.emit(this.formData);
    }

    /**
     * Dynamically creates the form object based on the given definition.
     * HTML must be created separately inside the HTML file as well.
     *
     * @param algorithmDefinition The definition.
     * @private
     */
    private createForm(algorithmDefinition: AlgorithmDefinition): FormGroup {
        const formGroup: any = {};

        Object.entries(algorithmDefinition.configurations).forEach(([name, groupDefinition]) => {
            const group: any = {};

            groupDefinition.parameters.forEach(inputDefinition => {
                if (inputDefinition.type === ConfigurationInputType.LIST) {
                    const controls = [];
                    for (const defaultValue of inputDefinition.default_value as number[]) {
                        controls.push(new FormControl(defaultValue, Validators.required));
                    }

                    group[inputDefinition.name] = new FormArray(controls, Validators.required)
                } else {
                    // Add validators of the input
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
