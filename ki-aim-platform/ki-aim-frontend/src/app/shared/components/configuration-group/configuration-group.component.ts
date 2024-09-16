import { Component, Input } from '@angular/core';
import { ConfigurationGroupDefinition } from "../../model/configuration-group-definition";
import { FormGroup } from "@angular/forms";

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

    constructor(
    ) {
    }

    /**
     * Gets the form object of this group.
     * @protected
     */
    protected get formGroup(): FormGroup {
        return this.form.controls[this.fromGroupName] as FormGroup;
    }

}
