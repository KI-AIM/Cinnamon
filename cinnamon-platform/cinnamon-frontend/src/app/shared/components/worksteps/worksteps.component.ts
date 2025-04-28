import { AfterViewInit, Component, ViewChild } from '@angular/core';
import { WorkstepService } from "../../services/workstep.service";
import { MatAccordion } from "@angular/material/expansion";

@Component({
    selector: 'app-worksteps',
    template: `
		<mat-accordion #accordion>
			<ng-content></ng-content>
		</mat-accordion>
    `,
    standalone: false,
    providers: [WorkstepService],
})
export class WorkstepsComponent implements AfterViewInit {

    @ViewChild('accordion') ac!: MatAccordion;

    constructor(private readonly workstepService: WorkstepService) {
    }

    ngAfterViewInit(): void {
        console.log('hallo');
        this.workstepService.accordion = this.ac;
    }
}
