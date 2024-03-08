import { AfterViewInit, Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { NgModel } from '@angular/forms';
import { List } from 'src/app/core/utils/list';
import { ColumnConfiguration } from 'src/app/shared/model/column-configuration';
import { DataScale } from 'src/app/shared/model/data-scale';
import { DataType } from 'src/app/shared/model/data-type';

@Component({
    selector: 'app-attribute-configuration',
    templateUrl: './attribute-configuration.component.html',
    styleUrls: ['./attribute-configuration.component.less'],
})
export class AttributeConfigurationComponent implements AfterViewInit {
    @Input() attrNumber: String; 
    @Input() column: ColumnConfiguration;

    @Output() onValidation = new EventEmitter<boolean>();

    @ViewChild("name") nameInput: NgModel;

    constructor() { }

    ngAfterViewInit(): void {
        this.nameInput.statusChanges?.subscribe(() => {
            this.onValidation.emit(this.nameInput.valid ?? true)
        });
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
