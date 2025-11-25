import { Component, Input } from "@angular/core";
import { FormGroup } from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";

@Component({
    selector: "app-dateformat",
    templateUrl: "./dateformat.component.html",
    styleUrls: ["./dateformat.component.less"],
    standalone: false
})
export class DateformatComponent {
    @Input() form: FormGroup;

    public constructor(
        protected readonly dialog: MatDialog,
    ) {
    }
}
