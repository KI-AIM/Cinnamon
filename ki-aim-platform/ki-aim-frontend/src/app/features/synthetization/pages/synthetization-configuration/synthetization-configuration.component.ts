import { Component } from '@angular/core';
import { TitleService } from "../../../../core/services/title-service.service";

@Component({
  selector: 'app-synthetization-configuration',
  templateUrl: './synthetization-configuration.component.html',
  styleUrls: ['./synthetization-configuration.component.less'],
})
export class SynthetizationConfigurationComponent {

    constructor(
        private titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Synthetization");
    }
}
