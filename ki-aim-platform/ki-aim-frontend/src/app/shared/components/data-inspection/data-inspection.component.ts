import { Component, Input, OnInit } from '@angular/core';
import { Observable, tap } from "rxjs";
import { DataConfiguration } from "../../model/data-configuration";
import { DataConfigurationService } from "../../services/data-configuration.service";
import { HttpClient } from "@angular/common/http";

@Component({
    selector: 'app-data-inspection',
    templateUrl: './data-inspection.component.html',
    styleUrls: ['./data-inspection.component.less']
})
export class DataInspectionComponent implements OnInit {
    @Input() public step!: string;

    protected dataConfiguration$: Observable<DataConfiguration>;
    protected catImage$: Observable<CatImageMetadata[]>;

    protected filterText: string;

    constructor(
        private readonly dataConfigurationService: DataConfigurationService,
        private readonly httpClient: HttpClient,
    ) {
    }

    ngOnInit(): void {
        this.dataConfiguration$ = this.dataConfigurationService.downloadDataConfiguration(this.step);
        this.catImage$ = this.httpClient.get<CatImageMetadata[]>("https://api.thecatapi.com/v1/images/search");
    }


    // protected getCatImage$(): Observable<CatImageMetadata> {
    //     return this.httpClient.get<CatImageMetadata>("https://api.thecatapi.com/v1/images/search");
    // }

}

class CatImageMetadata {
    id: string;
    url: string;
    width: number;
    height: number;
}
