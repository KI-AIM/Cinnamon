import { Component, OnInit } from '@angular/core';
import { TitleService } from "../../../../core/services/title-service.service";
import { AlgorithmService, ConfigurationInfo } from "../../../../shared/services/algorithm.service";
import { SynthetizationService } from "../../services/synthetization.service";
import { Observable } from "rxjs";
import { AnonymizationService } from "@features/anonymization/services/anonymization.service";

@Component({
    selector: 'app-synthetization-configuration',
    templateUrl: './synthetization-configuration.component.html',
    styleUrls: ['./synthetization-configuration.component.less'],
    providers: [
        {
            provide: AlgorithmService,
            useExisting: SynthetizationService
        },
    ],
    standalone: false
})
export class SynthetizationConfigurationComponent implements OnInit {

    protected configurationInfo$: Observable<ConfigurationInfo>;

    constructor(
        private readonly synthService: SynthetizationService,
        private titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Synthetization");
    }

    public ngOnInit(): void {
        this.configurationInfo$ = this.synthService.fetchInfo();
    }
}
