import {Component, Input, OnInit, TemplateRef} from '@angular/core';
import {ColumnConfiguration} from "../../model/column-configuration";
import {Observable} from "rxjs";
import {HttpClient} from "@angular/common/http";
import {MatDialog} from "@angular/material/dialog";

@Component({
    selector: 'app-data-inspection-attribute',
    templateUrl: './data-inspection-attribute.component.html',
    styleUrls: ['./data-inspection-attribute.component.less']
})
export class DataInspectionAttributeComponent implements OnInit {
    @Input() public configuration!: ColumnConfiguration;

    protected catImage$: Observable<CatImageMetadata[]>;

    constructor(
        private readonly httpClient: HttpClient,
        private matDialog: MatDialog,
    ) {
    }

    ngOnInit(): void {
        this.catImage$ = this.httpClient.get<CatImageMetadata[]>("https://api.thecatapi.com/v1/images/search");
    }

    protected openDetailsDialog(templateRef: TemplateRef<any>) {
        this.matDialog.open(templateRef, {
            disableClose: true,
            width: '60%'
        });
    }
}

class CatImageMetadata {
    id: string;
    url: string;
    width: number;
    height: number;
}
