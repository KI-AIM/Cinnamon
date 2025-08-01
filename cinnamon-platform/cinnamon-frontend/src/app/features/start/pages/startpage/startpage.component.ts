import { Component, OnInit } from '@angular/core';
import { TitleService } from 'src/app/core/services/title-service.service';
import { Mode } from 'src/app/core/enums/mode';
import { Steps } from 'src/app/core/enums/steps';
import { StatusService } from "../../../../shared/services/status.service";
import { Observable, of, switchMap } from "rxjs";
import { Router } from "@angular/router";
import { Status } from "../../../../shared/model/status";

@Component({
    selector: 'app-startpage',
    templateUrl: './startpage.component.html',
    styleUrls: ['./startpage.component.less'],
    providers: [],
    standalone: false
})
export class StartpageComponent implements OnInit {
    Mode = Mode;
    Steps = Steps;

    protected status$: Observable<Status>

    constructor(
        private readonly router: Router,
        public statusService: StatusService,
        private titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Welcome!");
    }

    ngOnInit(): void {
        this.status$ = this.statusService.status$;
    }

    /**
     * Selects the mode, sets the next step, and navigates to the next page.
     * @param mode The selected mode.
     * @protected
     */
    protected selectMode(mode: Mode) {
        const isCompleted = this.statusService.isStepCompleted(Steps.WELCOME);

        this.statusService.setMode(mode).pipe(
            switchMap(() => {
                if (!isCompleted) {
                    return this.statusService.updateNextStep(Steps.UPLOAD);
                } else {
                    return of(null);
                }
            }),
        ).subscribe({
            next: () => {
                if (!isCompleted) {
                    this.router.navigateByUrl('/upload');
                }
            },
            error: (err) => {
                console.log(err)
            }
        });
    }
}
