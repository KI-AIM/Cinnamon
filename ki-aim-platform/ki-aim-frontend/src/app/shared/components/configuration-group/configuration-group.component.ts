import {Component, Input, QueryList, ViewChild, ViewChildren} from '@angular/core';
import { ConfigurationGroupDefinition } from "../../model/configuration-group-definition";
import {Form, FormGroup} from "@angular/forms";
import {ConfigurationFormComponent} from "../configuration-form/configuration-form.component";
import {ConfigurationInputComponent} from "../configuration-input/configuration-input.component";
import {MatCheckboxChange} from "@angular/material/checkbox";

/**
 * Component for a collapsable input group.
 */
@Component({
  selector: 'app-configuration-group',
  templateUrl: './configuration-group.component.html',
  styleUrls: ['./configuration-group.component.less']
})
export class ConfigurationGroupComponent {

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
     * List of all group components that are options.
     * @private
     */
    @ViewChildren('options') private groups: QueryList<ConfigurationGroupComponent>;

    constructor(
    ) {
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
        for (const group of this.groups) {
            if (!group.isActive) {
                delete groupConfig[group.fromGroupName];
            }
        }

        return groupConfig;
    }

    /**
     * Gets the form object of this group.
     * @protected
     */
    protected get formGroup(): FormGroup {
        if (this.isOption) {
            return this.form.controls[this.fromGroupName] as FormGroup;
        } else {
            return this.form.controls[this.fromGroupName] as FormGroup;
        }
    }

    /**
     * Toggles the active status of the group corresponding to the checkbox in the given event.
     * @param event The change event of the checkbox.
     * @protected
     */
    protected toggleActive(event: MatCheckboxChange): void {
        for (const group of this.groups) {
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

}
