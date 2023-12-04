import { Component, Input } from '@angular/core';
import { List } from 'src/app/core/utils/list';
import { DataScale } from 'src/app/shared/model/data-scale';
import { DataType } from 'src/app/shared/model/data-type';

@Component({
    selector: 'app-attribute-configuration',
    templateUrl: './attribute-configuration.component.html',
    styleUrls: ['./attribute-configuration.component.less'],
})
export class AttributeConfigurationComponent {
    @Input() attrNumber: String; 
    @Input() columnType: DataType;
    @Input() dataScale: DataScale
    @Input() columnName: String; 
    

    constructor() {
    }



    getDataTypes(): List<String> {
        const types = Object.keys(DataType).filter(x => !(parseInt(x) >= 0));

        return new List<String>(types); 
    }

    getDataScales(): List<String> {
        const scales = Object.keys(DataScale).filter(x => !(parseInt(x) >= 0)); 

        return new List<String>(scales); 
    }

    parseInt(value: String): Number {
        return Number(value); 
    }
}
