import { AfterViewInit, Component, Host, Input, OnInit, Optional, ViewChild } from '@angular/core';
import { MatAccordion, MatExpansionPanel } from "@angular/material/expansion";
import { WorkstepService } from "../../services/workstep.service";
import { timeout } from "rxjs";

@Component({
    selector: 'app-workstep',
    templateUrl: './workstep.component.html',
    styleUrl: './workstep.component.less',
    standalone: false
})
export class WorkstepComponent implements OnInit, AfterViewInit {
    @Input() public currentStep!: number;
    @Input() public stepIndex!: number;
    @Input() public title!: string;

    @ViewChild(MatExpansionPanel) expansionPanel!: MatExpansionPanel;


    constructor(
        private readonly workstepService: WorkstepService,
        @Optional() @Host() private readonly accordion: MatAccordion,
        // private readonly expansionPanel: MatExpansionPanel,
    ) {
    }

    ngOnInit(): void {
        // if (this.accordion) {
        //     this.expansionPanel.accordion = this.accordion;
        // }
    }

    ngAfterViewInit(): void {
        console.log('hi');
        this.expansionPanel.accordion = this.workstepService.accordion!;
    }
}
