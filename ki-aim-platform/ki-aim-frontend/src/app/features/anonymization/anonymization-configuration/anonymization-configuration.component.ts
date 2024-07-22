import { Component, Input } from '@angular/core';
import { ConfigurationInputDefinition } from "../../../shared/model/configuration-input-definition";
import { ConfigurationInputType } from "../../../shared/model/configuration-input-type";

@Component({
  selector: 'app-anonymization-configuration',
  templateUrl: './anonymization-configuration.component.html',
  styleUrls: ['./anonymization-configuration.component.less']
})
export class AnonymizationConfigurationComponent {

    abc: ConfigurationInputDefinition[];
    defs: {[name :string]: ConfigurationInputDefinition[]} = {};

    constructor() {

        this.abc = [] as ConfigurationInputDefinition[];

        const floatInput = new ConfigurationInputDefinition();
        floatInput.name = "float";
        floatInput.type = ConfigurationInputType.FLOAT;
        floatInput.label = "Float";
        floatInput.defaultValue = 0.3;
        floatInput.minValue = 0;
        floatInput.maxValue = 1;
        floatInput.description = "Beschreibung";
        this.abc.push(floatInput);

        const integerInput = new ConfigurationInputDefinition();
        integerInput.name = "int";
        integerInput.type = ConfigurationInputType.INTEGER;
        integerInput.label = "Int";
        integerInput.defaultValue = 2;
        integerInput.minValue = 0;
        integerInput.maxValue = 100;
        this.abc.push(integerInput);

        const stringInput = new ConfigurationInputDefinition();
        stringInput.name = "toll";
        stringInput.type = ConfigurationInputType.STRING;
        stringInput.label = "Text";
        stringInput.defaultValue = "abc";
        this.abc.push(stringInput);

        const stringSelect = new ConfigurationInputDefinition();
        stringSelect.name = "select";
        stringSelect.type = ConfigurationInputType.STRING;
        stringSelect.label = "Select";
        stringSelect.defaultValue = "abc";
        stringSelect.values = ["abc", "def", "hij"];
        this.abc.push(stringSelect);

        const arrayInput = new ConfigurationInputDefinition();
        arrayInput.name = "array";
        arrayInput.type = ConfigurationInputType.ARRAY;
        arrayInput.label = "Array";
        arrayInput.defaultValue = [1, 2, 3];
        this.abc.push(arrayInput);

        this.defs["Den günstigsten den Sie haben. Ist für meine Schwiegereltern."] = this.abc;

        const oho = [] as ConfigurationInputDefinition[];
        const stringInput2 = new ConfigurationInputDefinition();
        stringInput2.name = "toll";
        stringInput2.type = ConfigurationInputType.STRING;
        stringInput2.label = "Text";
        stringInput2.defaultValue = "abc";
        oho.push(stringInput2);
        this.defs["Mir egal, entschied du :)"] = oho;
    }
}
