import {Component, ComponentFactoryResolver, ElementRef, EventEmitter, Input, OnInit, Output, QueryList, ViewChildren, ViewChild, ViewContainerRef } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from "@angular/forms";
import { ConfigurationInputType } from "../../model/configuration-input-type";
import { AlgorithmDefinition } from "../../model/algorithm-definition";
import { AlgorithmService } from "../../services/algorithm.service";
import { Algorithm } from "../../model/algorithm";
import { ConfigurationGroupDefinition } from "../../model/configuration-group-definition";
import {ConfigurationGroupComponent} from "../configuration-group/configuration-group.component";
import { ConfigurationAdditionalConfigs } from '../../model/configuration-additional-configs';
import { HttpErrorResponse } from "@angular/common/http";

/**
 * HTML form and submit button for one algorithm.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'app-configuration-form',
    templateUrl: './configuration-form.component.html',
    styleUrls: ['./configuration-form.component.less'],
})
export class ConfigurationFormComponent implements OnInit {

    @Input() additionalConfigs: ConfigurationAdditionalConfigs | null = null

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

    public formInput: {form: FormGroup};

    /**
     * Event that gets triggered on every change.
     */
    @Output() public onChange: EventEmitter<void> = new EventEmitter();

    /**
     * Event for submitting the configurations.
     * Emits the raw JSON of the form.
     */
    @Output() public submitConfiguration = new EventEmitter<Object>();

    /**
     * Event that gets triggered when an error occurs in the form.
     */
    @Output() public onError: EventEmitter<string> = new EventEmitter();

    @ViewChildren(ConfigurationGroupComponent) private groups: QueryList<ConfigurationGroupComponent>;

    @ViewChild('dynamicComponentContainer', {read: ViewContainerRef}) componentContainer: ViewContainerRef;

    constructor(
        private readonly anonService: AlgorithmService,
    ) {
       this.form = new FormGroup({});
    }

    ngOnInit() {
        // Fetch the configuration definition.
        this.anonService.getAlgorithmDefinition(this.algorithm)
            .subscribe({
                next:
                    value => {
                        this.algorithmDefinition = value
                        this.form = this.createForm(value);
                        this.updateForm();

                        this.formInput = {
                            form: this.form,
                        };

                        this.loadComponents();

                        this.form.valueChanges.subscribe(value => {
                            this.onChange.emit();
                        });
                    },
                error: (err: HttpErrorResponse) => {
                    this.onError.emit(`Failed to load algorithm definition! Status: ${err.status} (${err.statusText})`);
                },
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
     * Removes option groups that are not selected.
     */
    public get formData(): Object {
        return this.removeUncheckedGroups(this.form.getRawValue());
    }

    /**
     * Sets the values of the form from the given JSON object.
     * @param configuration JSON of the form.
     */
    public setConfiguration(configuration: Object) {
        //Wait here so that form is loaded before updating it
        setTimeout(() => {
            for (const group of this.groups) {
                group.handleMissingOptions(configuration);
            }
            this.form.patchValue(configuration);
        }, 100);
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
        if (algorithmDefinition.configurations) {
            this.createGroups(formGroup, algorithmDefinition.configurations);
        }
        if (algorithmDefinition.options) {
            this.createGroups(formGroup, algorithmDefinition.options);
        }
        return new FormGroup(formGroup);
    }

    private createGroups(formGroup: any, configurations: { [name: string]: ConfigurationGroupDefinition }) {
        Object.entries(configurations).forEach(([name, groupDefinition]) => {
            formGroup[name] = this.createGroup(groupDefinition);
        });
    }

    private createGroup(groupDefinition: ConfigurationGroupDefinition): FormGroup {
        const group: any = {};

        if (groupDefinition.parameters) {
            groupDefinition.parameters.forEach(inputDefinition => {
                if (inputDefinition.type === ConfigurationInputType.LIST) {
                    const controls = [];
                    for (const defaultValue of inputDefinition.default_value as number[]) {
                        controls.push(new FormControl({value: defaultValue, disabled: this.disabled}, Validators.required));
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

                    group[inputDefinition.name] = new FormControl({value: inputDefinition.default_value, disabled: this.disabled} , validators)
                }
            });
        }

        if (groupDefinition.configurations) {
            this.createGroups(group, groupDefinition.configurations);
        }
        if (groupDefinition.options) {
            this.createGroups(group, groupDefinition.options);
        }

        return new FormGroup(group);
    }

    /**
     * Removes all configuration properties of the given object that belongs to a group that is not selected.
     * @param object The configuration object to be modified.
     * @private
     */
    private removeUncheckedGroups(object: any): any {
        for (const group of this.groups) {
            group.removeInactiveGroups(object[group.fromGroupName]);
        }

        return object;
    }

    /**
     * Loads additional config components 
     * and injects them into the component container.
     * Also attaches the form to the component
     */
    loadComponents() {
        this.additionalConfigs?.configs.forEach(config => {
            this.componentContainer.clear();
            var componentRef: any = this.componentContainer.createComponent(config.component);
            componentRef.instance.form = this.formInput.form;
        });
    }
}
