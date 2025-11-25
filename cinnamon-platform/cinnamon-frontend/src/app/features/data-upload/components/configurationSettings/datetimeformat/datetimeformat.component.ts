import { Component, Input } from "@angular/core";
import { FormGroup } from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";

@Component({
    selector: "app-datetimeformat",
    templateUrl: "./datetimeformat.component.html",
    styleUrls: ["./datetimeformat.component.less"],
    standalone: false
})
export class DatetimeformatComponent {
    @Input() form: FormGroup;

    public constructor(
        protected readonly dialog: MatDialog,
    ) {
    }
}
