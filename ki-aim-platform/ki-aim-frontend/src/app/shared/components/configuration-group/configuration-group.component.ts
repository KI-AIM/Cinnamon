import { Component, Input } from '@angular/core';
import { ConfigurationGroupDefinition } from "../../model/configuration-group-definition";
import { FormGroup } from "@angular/forms";

@Component({
  selector: 'app-configuration-group',
  templateUrl: './configuration-group.component.html',
  styleUrls: ['./configuration-group.component.less']
})
export class ConfigurationGroupComponent {

    @Input() public form!: FormGroup;
    @Input() public group: ConfigurationGroupDefinition;

    constructor(
    ) {
    }

}
