import {ChangeDetectionStrategy, Component, Input, OnInit} from '@angular/core';
import {Observable} from "rxjs";
import {DataConfiguration} from "../../model/data-configuration";
import {DataConfigurationService} from "../../services/data-configuration.service";

@Component({
    selector: 'app-data-inspection',
    templateUrl: './data-inspection.component.html',
    styleUrls: ['./data-inspection.component.less']
})
export class DataInspectionComponent implements OnInit {
    @Input() public step!: string;

    protected dataConfiguration$: Observable<DataConfiguration>;

    protected filterText: string;
    protected filterMetaData: {count: number} = {count: 0};

    constructor(
        private readonly dataConfigurationService: DataConfigurationService,
    ) {
    }

    ngOnInit(): void {
        this.dataConfiguration$ = this.dataConfigurationService.downloadDataConfiguration(this.step);
    }
}
