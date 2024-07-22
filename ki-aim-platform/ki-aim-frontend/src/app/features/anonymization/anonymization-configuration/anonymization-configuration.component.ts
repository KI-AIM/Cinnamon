import { Component } from '@angular/core';
import { ConfigurationInputDefinition } from "../../../shared/model/configuration-input-definition";
import { ConfigurationInputType } from "../../../shared/model/configuration-input-type";

@Component({
  selector: 'app-anonymization-configuration',
  templateUrl: './anonymization-configuration.component.html',
  styleUrls: ['./anonymization-configuration.component.less']
})
export class AnonymizationConfigurationComponent {

    abc: ConfigurationInputDefinition[];

    constructor() {

        this.abc = [];

        const floatInput = new ConfigurationInputDefinition();
        floatInput.name = "float";
        floatInput.type = ConfigurationInputType.FLOAT;
        floatInput.label = "Float";
        floatInput.defaultValue = 0.3;
        this.abc.push(floatInput);

        const integerInput = new ConfigurationInputDefinition();
        integerInput.name = "int";
        integerInput.type = ConfigurationInputType.INTEGER;
        integerInput.label = "Int";
        integerInput.defaultValue = 2;
        this.abc.push(integerInput);

        const stringInput = new ConfigurationInputDefinition();
        stringInput.name = "toll";
        stringInput.type = ConfigurationInputType.STRING;
        stringInput.label = "Text";
        stringInput.defaultValue = "abc";
        this.abc.push(stringInput);

        const stringSelect = new ConfigurationInputDefinition();
        stringSelect.name = "toll";
        stringSelect.type = ConfigurationInputType.STRING;
        stringSelect.label = "Select";
        stringSelect.defaultValue = "abc";
        stringSelect.values = ["abc", "def", "hij"];
        this.abc.push(stringSelect);

    }
}
