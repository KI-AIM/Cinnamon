import {
    AfterViewInit,
    Component,
    ComponentRef,
    Input,
    OnChanges,
    QueryList,
    SimpleChanges,
    ViewChild,
    ViewChildren,
    ViewContainerRef
} from '@angular/core';
import { AdditionalConfigurationGroup } from "@shared/interfaces/AdditionalConfigurationGroup";
import { ConfigurationGroupDefinition, VisualizationType } from "../../model/configuration-group-definition";
import { FormGroup } from "@angular/forms";
import {ConfigurationInputComponent} from "../configuration-input/configuration-input.component";
import { MatCheckbox, MatCheckboxChange } from "@angular/material/checkbox";
import { ConfigurationAdditionalConfigs } from "../../model/configuration-additional-configs";

/**
 * Component for a collapsable input group.
 */
@Component({
    selector: 'app-configuration-group',
    templateUrl: './configuration-group.component.html',
    styleUrls: ['./configuration-group.component.less'],
    standalone: false
})
export class ConfigurationGroupComponent implements AfterViewInit, OnChanges {
    protected readonly VisualizationType = VisualizationType;

    /**
     * Configurations displayed additionally to the given definition.
     */
    @Input() additionalConfigs: ConfigurationAdditionalConfigs | null = null

    /**
     * The parent form object.
     */
    @Input() public form!: FormGroup;

    /**
     * The name of the group inside the form object.
     */
    @Input() public fromGroupName!: string;

    /**
     * The definition for the group.
     */
    @Input() public group!: ConfigurationGroupDefinition;

    /**
     * If the group is disabled.
     */
    @Input() public disabled!: boolean;

    /**
     * If the group is an option.
     */
    @Input() public isOption!: boolean;

    /**
     * Active status of this group.
     * @protected
     */
    protected isActive: boolean = true;

    /**
     * List of all inputs components in this group.
     * @private
     */
    @ViewChildren(ConfigurationInputComponent) private inputs: QueryList<ConfigurationInputComponent>;

    /**
     * List of all checkboxes for option groups.
     * @private
     */
    @ViewChildren('optionCheckboxes') private matCheckboxes: QueryList<MatCheckbox>;

    /**
     * List of all group components that are configurations.
     * @private
     */
    @ViewChildren('configurations') private configurations: QueryList<ConfigurationGroupComponent>;

    /**
     * List of all group components that are options.
     * @private
     */
    @ViewChildren('options') private options: QueryList<ConfigurationGroupComponent>;

    /**
     * Container for the additional configs.
     */
    @ViewChild('dynamicComponentContainer', {read: ViewContainerRef}) componentContainer: ViewContainerRef;

    /**
     * Component refs for additional configurations.
     * @private
     */
    private instances: ComponentRef<AdditionalConfigurationGroup>[] = [];

    public ngAfterViewInit() {
        this.loadComponents();
    }

    /**
     * Listens to changes on the disabled input and forwards the new value to all additional configuration components
     * @param changes The changes.
     */
    public ngOnChanges(changes: SimpleChanges): void {
        if (changes['disabled'] != null && !changes['disabled'].firstChange) {
            const disabled = changes['disabled'].currentValue;

            for (const input of this.inputs) {
                input.setDisabled(disabled);
            }

            this.instances.forEach((instance) => {
                instance.setInput('disabled', disabled);
            });
        }
    }

    /**
     * Sets the active status of this group.
     * Disables all inputs if the group is set to inactive.
     * @param active If the group should be active.
     */
    public setActive(active: boolean) {
        this.disabled = !active;
        this.isActive = active;
        for (const abc of this.inputs) {
            abc.setDisabled(!active);
        }
    }

    /**
     * Removes inactive groups from the given configuration object.
     * @param groupConfig The configuration object.
     */
    public removeInactiveGroups(groupConfig: any): any {
        let configGroup;
        if (this.fromGroupName) {
            configGroup = groupConfig[this.fromGroupName];
        } else {
            configGroup = groupConfig;
        }

        for (const group of this.configurations) {
            group.removeInactiveGroups(configGroup);
        }
        for (const group of this.options) {
            group.removeInactiveGroups(configGroup);

            if (!group.isActive) {
                delete configGroup[group.fromGroupName];
            }
        }

        return configGroup;
    }

    /**
     * Deactivates options that are not provided in the given configuration.
     * @param configuration The configuration object.
     */
    public handleMissingOptions(configuration: Record<string, any>) {
       if (Object.keys(configuration).length === 0)  {
           return;
       }

        let configGroup;
        if (this.fromGroupName) {
            configGroup = configuration[this.fromGroupName];
        } else {
            configGroup = configuration;
        }

        if (this.group.options) {
            for (const def of this.options) {
                if (!Object.hasOwn(configGroup, def.fromGroupName)) {
                    for (const cb of this.matCheckboxes) {
                        if (cb.id === 'isActive' + def.fromGroupName) {
                            cb.checked = false;
                            break;
                        }
                    }
                    def.setActive(false);
                }
            }
        }

        for (const group of this.configurations) {
            group.handleMissingOptions(configGroup);
        }
        for (const group of this.options) {
            group.handleMissingOptions(configGroup);
        }
    }

    /**
     * Gets the form object of this group.
     * @protected
     */
    protected get formGroup(): FormGroup {
        if (this.fromGroupName) {
            return this.form.controls[this.fromGroupName] as FormGroup;
        } else {
            return this.form;
        }
    }

    /**
     * Toggles the active status of the group corresponding to the checkbox in the given event.
     * @param event The change event of the checkbox.
     * @protected
     */
    protected toggleActive(event: MatCheckboxChange): void {
        for (const group of this.options) {
            if (group.fromGroupName === event.source.value) {
                group.setActive(event.checked);
                break;
            }
        }
    }

    /**
     * Checks if the given group definition does not contain any children.
     * @param group The group definition.
     * @protected
     */
    protected isGroupEmpty(group: ConfigurationGroupDefinition) {
        return (!group.parameters || group.parameters.length === 0) && !group.configurations && !group.options;
    }

    /**
     * Loads additional config components
     * and injects them into the component container.
     * Also attaches the form to the component
     */
    private loadComponents() {
        if (this.additionalConfigs !== null) {
            this.additionalConfigs?.configs.forEach(config => {
                const componentRef: ComponentRef<AdditionalConfigurationGroup> = this.componentContainer.createComponent(config.component);
                componentRef.setInput('disabled', this.disabled);
                componentRef.instance.form = this.form;
                this.instances.push(componentRef);
            });
        }
    }

    /**
     * Patches the values of the additional configurations groups.
     * @param obj The configuration object.
     */
    public patchComponents(obj: any) {
        if (this.additionalConfigs === null) {
            return;
        }

        this.additionalConfigs.configs.forEach((config, i) => {
            this.instances[i].instance.patchValue(obj[config.formGroupName]);
        });
    }
}
