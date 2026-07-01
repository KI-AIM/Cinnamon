import { Component, OnInit } from '@angular/core';
import { Router } from "@angular/router";
import { ErrorHandlingService } from "@shared/services/error-handling.service";

@Component({
    standalone: false,
    template: ''
})
export class NotFoundComponent implements OnInit {

    public constructor(
        private router: Router,
        private errorHandlingService: ErrorHandlingService,
    ) {
    }

    public ngOnInit(): void {
        this.errorHandlingService.addError("The page " + this.router.url + " was not found.");
        this.router.navigateByUrl("/");
    }

}
