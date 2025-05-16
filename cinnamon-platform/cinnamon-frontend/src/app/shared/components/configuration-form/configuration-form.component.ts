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
import { catchError, Observable, of, switchMap, tap } from "rxjs";
import { ConfigurationInputType } from "../../model/configuration-input-type";
import { AlgorithmDefinition } from "../../model/algorithm-definition";
import { AlgorithmService } from "../../services/algorithm.service";
import { Algorithm } from "../../model/algorithm";
import {
    ConfigurationGroupDefinition,
} from "../../model/configuration-group-definition";
import { ConfigurationGroupComponent } from "../configuration-group/configuration-group.component";
import { ConfigurationAdditionalConfigs } from '../../model/configuration-additional-configs';
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
     * Observable that loads the configurations and creates the form group.
     * @protected
     */
    protected configurationData$: Observable<any>;

    /**
     * Event that gets triggered on every change.
     */
    @Output() public onChange: EventEmitter<boolean> = new EventEmitter();

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

        this.configurationData$ = this.anonService.getAlgorithmDefinition(this.algorithm).pipe(
            tap(value => {
                this.algorithmDefinition = value
            }),
            switchMap(_ => {
               return this.anonService.fetchConfiguration();
            }),
            tap(value => {

                this.form = this.createForm(this.algorithmDefinition!, value.config);

                this.doSetConfiguration(this.algorithmDefinition!, this.form, value.config);
                this.updateForm();

                this.form.valueChanges.pipe().subscribe(_ => {
                    this.onChange.emit(this.valid);
                });

                this.onChange.emit(this.valid);
            }),
            catchError(err => {
                this.errorHandlingService.addError(err, "Failed to load the configuration page. You can skip this step for now or try again later.");
                return of(null);
            }),
        );
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
        const a = this.configurationService.getConfiguration(this.anonService.getConfigurationName(), this.algorithm)
        if (a) {
            this.doSetConfiguration(this.algorithmDefinition!, this.form, a);
        }
    }

    private doSetConfiguration(algorithmDefinition: AlgorithmDefinition, form: FormGroup, configuration: Object) {
        if (Object.keys(configuration).length === 0) {
            return;
        }

        this.fixAttributeLists(algorithmDefinition, configuration, form);
        this.form.patchValue(configuration);
        setTimeout(() => {
            // Has to be run after the page is initialized
            for (const group of this.groups) {
                group.handleMissingOptions(configuration);
            }
            this.rootGroup.patchComponents(configuration);
            this.changeD.detectChanges();
        })
    }

    private fixAttributeLists(cde: ConfigurationGroupDefinition, obj: Object, form: FormGroup) {
        if (cde.options) {
            for (const [key, value] of Object.entries(cde.options)) {
                const groupConfiguration = (obj as Record<string, any>)[key];
                // The configuration is not available if the option is not checked
                if (groupConfiguration) {
                    this.fixAttributeLists(value, (obj as Record<string, any>)[key], form.controls[key] as FormGroup);
                }
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
     * Dynamically creates the form object based on the given definition.
     * HTML must be created separately inside the HTML file as well.
     *
     * @param algorithmDefinition The definition.
     * @param initialValues
     * @private
     */
    private createForm(algorithmDefinition: AlgorithmDefinition, initialValues: any): FormGroup {
        const form = this.createGroup(algorithmDefinition);

        if (this.additionalConfigs) {
            for (const additionalConfig of this.additionalConfigs.configs) {
                const config = initialValues[additionalConfig.formGroupName] ? initialValues[additionalConfig.formGroupName] : null;
                additionalConfig.initializeForm(form, config, this.disabled);
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
