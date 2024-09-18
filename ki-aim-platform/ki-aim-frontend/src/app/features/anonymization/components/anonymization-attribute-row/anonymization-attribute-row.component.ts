import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { ColumnConfiguration } from 'src/app/shared/model/column-configuration';
import { NgModel } from '@angular/forms';


@Component({
    selector: 'app-anonymization-attribute-row',
    templateUrl: './anonymization-attribute-row.component.html',
    styleUrls: ['./anonymization-attribute-row.component.css'],
})
export class AnonymizationAttributeRowComponent implements OnInit {
    @Input() configurationRow: ColumnConfiguration;
    @Input() rowNumber: number; 

    @ViewChild("name") nameInput: NgModel;

    constructor() {

    }

    ngOnInit() {

    }
}
