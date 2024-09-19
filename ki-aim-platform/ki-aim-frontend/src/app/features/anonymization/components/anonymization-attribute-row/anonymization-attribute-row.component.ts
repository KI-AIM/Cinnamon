import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from '@angular/core';
import { ColumnConfiguration } from 'src/app/shared/model/column-configuration';
import { NgModel } from '@angular/forms';
import { AnonymizationAttributeRowConfiguration } from 'src/app/shared/model/anonymization-attribute-row-configuration';
import { AnonymizationAttributeType } from 'src/app/shared/model/anonymization-attribute-type.enum';
import { AnonymizationTransformationType } from 'src/app/shared/model/anonymization-transformation-type.enum';
import { List } from 'src/app/core/utils/list';


@Component({
    selector: 'app-anonymization-attribute-row',
    templateUrl: './anonymization-attribute-row.component.html',
    styleUrls: ['./anonymization-attribute-row.component.css'],
})
export class AnonymizationAttributeRowComponent implements OnInit {
    @Input() configurationRow: ColumnConfiguration;
    @Input() anonymizationRowConfiguration: AnonymizationAttributeRowConfiguration; 

    @Output() removeEvent = new EventEmitter<string>();

    @ViewChild("name") nameInput: NgModel;

    AnonymizationAttributeType = AnonymizationAttributeType; 
    AnonymizationTransformationType = AnonymizationTransformationType; 

    constructor() {
    }

    ngOnInit() {
        
    }

    removeCurrentRow() {
        this.removeEvent.emit('removeEvent');
    }

    getAllTypes() {
        const types = Object.keys(AnonymizationAttributeType).filter(x => !(parseInt(x) >= 0));

        return new List<String>(types);
    }

    getAllTransformations() {
        const types = Object.keys(AnonymizationTransformationType).filter(x => !(parseInt(x) >= 0));

        return new List<String>(types);
    }

}
