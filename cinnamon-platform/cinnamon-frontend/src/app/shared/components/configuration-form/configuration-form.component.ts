import {
    ChangeDetectorRef,
    Component,
    EventEmitter,
    Input,
    OnInit,
    Output,
    QueryList,
    ViewChild,
    ViewChildren
} from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from "@angular/forms";
import { ConfigurationInputType } from "../../model/configuration-input-type";
import { AlgorithmDefinition } from "../../model/algorithm-definition";
import { AlgorithmService } from "../../services/algorithm.service";
import { Algorithm } from "../../model/algorithm";
import {
    ConfigurationGroupDefinition,
} from "../../model/configuration-group-definition";
import {ConfigurationGroupComponent} from "../configuration-group/configuration-group.component";
import { ConfigurationAdditionalConfigs } from '../../model/configuration-additional-configs';
import { HttpErrorResponse } from "@angular/common/http";
import { ErrorHandlingService } from "../../services/error-handling.service";
import { ConfigurationService } from "../../services/configuration.service";

/**
 * HTML form and submit button for one algorithm.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'app-configuration-form',
    templateUrl: './configuration-form.component.html',
    styleUrls: ['./configuration-form.component.less'],
    standalone: false
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
    protected algorithmDefinition: AlgorithmDefinition | null = null;
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

    @ViewChildren(ConfigurationGroupComponent) private groups: QueryList<ConfigurationGroupComponent>;

    @ViewChild(ConfigurationGroupComponent) private rootGroup: ConfigurationGroupComponent;

    constructor(
        private readonly anonService: AlgorithmService,
        private readonly changeD: ChangeDetectorRef,
        private readonly configurationService: ConfigurationService,
        private readonly errorHandlingService: ErrorHandlingService,
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
                        this.readFromCache();

                        this.form.valueChanges.subscribe(() => {
                            this.onChange.emit();
                        });
                    },
                error: (err: HttpErrorResponse) => {
                    this.errorHandlingService.addError(err, "Failed to load the configuration page. You can skip this step for now or try again later.");
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
     * Reads the configuration from the cache.
     */
    public readFromCache(): void {
        if (this.configurationService.getConfiguration(this.anonService.getConfigurationName(), this.algorithm)) {
            //Timeout is 0, so function is called before data is overwritten
            setTimeout(() => {
                const config = this.configurationService.getSelectedConfiguration(this.anonService.getConfigurationName());
                // config can be undefined if no changes have been made
                if (config) {
                    this.setConfiguration(config);
                }
            }, 0);
        }
    }

    /**
     * Sets the values of the form from the given JSON object.
     * @param configuration JSON of the form.
     */
    public setConfiguration(configuration: Object) {
        //Wait here so that form is loaded before updating it
        setTimeout(() => {
            if (this.algorithmDefinition === null || Object.keys(this.form.controls).length === 0) {
                return;
            }

            this.fixAttributeLists(this.algorithmDefinition, configuration, this.form);
            for (const group of this.groups) {
                group.handleMissingOptions(configuration);
            }
            this.form.patchValue(configuration);
            this.rootGroup.patchComponents(configuration);
            this.changeD.detectChanges();
        }, 100);
    }

    private fixAttributeLists(cde: ConfigurationGroupDefinition, obj: Object, form: FormGroup) {
        if (cde.options) {
            for (const [key, value] of Object.entries(cde.options)) {
                this.fixAttributeLists(value, (obj as Record<string, any>)[key], form.controls[key] as FormGroup);
            }
        }
        if (cde.configurations) {
            for (const [key, value] of Object.entries(cde.configurations)) {
                this.fixAttributeLists(value, (obj as Record<string, any>)[key] as Object, form.controls[key] as FormGroup);
            }
        }
        if (cde.parameters) {
            for (const key of cde.parameters) {
                if (key.type === ConfigurationInputType.ATTRIBUTE_LIST) {

                    const list = (obj as Record<string, any>)[key.name];
                    if (Array.isArray(list)) {
                        (form.get(key.name) as FormArray).clear();
                        for (const item of list) {
                            (form.get(key.name) as FormArray).push(new FormControl());
                        }
                    }

                    if (key.invert) {
                        const invert = (obj as Record<string, any>)[key.invert];
                        if (Array.isArray(invert)) {
                            (form.get(key.invert) as FormArray).clear();
                            for (const item of invert) {
                                (form.get(key.invert) as FormArray).push(new FormControl());
                            }
                        }
                    }
                }
            }
        }
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
        const form = this.createGroup(algorithmDefinition);

        if (this.additionalConfigs) {
            for (const additionalConfig of this.additionalConfigs.configs) {
                additionalConfig.initializeForm(form);
            }
        }

        return form;
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
                        controls.push(new FormControl({
                            value: defaultValue,
                            disabled: this.disabled
                        }, Validators.required));
                    }

                    group[inputDefinition.name] = new FormArray(controls, Validators.required);
                } else if (inputDefinition.type === ConfigurationInputType.ATTRIBUTE_LIST) {
                    group[inputDefinition.name] = new FormArray([], Validators.required);
                    if (inputDefinition.invert) {
                        group[inputDefinition.invert] = new FormArray([], Validators.required);
                    }
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
            group.removeInactiveGroups(object);
        }

        return object;
    }
}
