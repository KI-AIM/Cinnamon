import {Component, Input, OnInit} from '@angular/core';
import {ColumnConfiguration} from "../../model/column-configuration";
import {Observable} from "rxjs";
import {HttpClient} from "@angular/common/http";

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
    ) {
    }

    ngOnInit(): void {
        this.catImage$ = this.httpClient.get<CatImageMetadata[]>("https://api.thecatapi.com/v1/images/search");
    }
}

class CatImageMetadata {
    id: string;
    url: string;
    width: number;
    height: number;
}
