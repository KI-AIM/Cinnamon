import { Component, OnInit } from '@angular/core';
import { StateManagementService } from "@core/services/state-management.service";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { Mode } from 'src/app/core/enums/mode';
import { Steps } from 'src/app/core/enums/steps';
import { StatusService } from "../../../../shared/services/status.service";
import { Observable, of, switchMap } from "rxjs";
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
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly stateManagementService: StateManagementService,
        public statusService: StatusService,
    ) {
    }

    ngOnInit(): void {
        this.status$ = this.statusService.statusNonNull$;
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
                    return this.stateManagementService.setAndRouteToStep(Steps.UPLOAD);
                } else {
                    return of(null);
                }
            }),
        ).subscribe({
            error: (err) => {
                this.errorHandlingService.addError(err);
            }
        });
    }
}
