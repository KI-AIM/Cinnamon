import { Component } from '@angular/core';
import { TitleService } from 'src/app/core/services/title-service.service';
import { Mode } from 'src/app/core/enums/mode';
import { Steps } from 'src/app/core/enums/steps';
import { StatusService } from "../../../../shared/services/status.service";
import { switchMap } from "rxjs";
import { Router } from "@angular/router";

@Component({
    selector: 'app-startpage',
    templateUrl: './startpage.component.html',
    styleUrls: ['./startpage.component.less'],
    providers: []
})
export class StartpageComponent {
    Mode = Mode;
    Steps = Steps;

    constructor(
        private readonly router: Router,
        public statusService: StatusService,
        private titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Welcome!");
    }

    protected get locked(): boolean {
        return this.statusService.isStepCompleted(Steps.UPLOAD);
    }

    /**
     * Selects the mode, sets the next step and navigates to the next page.
     * @param mode The selected mode.
     * @protected
     */
    protected selectMode(mode: Mode) {
        this.statusService.setMode(mode).pipe(
            switchMap(() => {
                return this.statusService.updateNextStep(Steps.UPLOAD);
            }),
        ).subscribe({
            next: () => {
                this.router.navigateByUrl('/upload');
            },
            error: (err) => {
                console.log(err)
            }
        });
    }
}
