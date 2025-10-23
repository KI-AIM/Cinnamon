import { Component, Input } from "@angular/core";
import { FormGroup } from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";

@Component({
    selector: "app-stringpattern",
    templateUrl: "./stringpattern.component.html",
    styleUrls: ["./stringpattern.component.less"],
    standalone: false
})
export class StringpatternComponent {
    @Input() form: FormGroup;

    public constructor(
        protected readonly dialog: MatDialog,
    ) {
    }
}
