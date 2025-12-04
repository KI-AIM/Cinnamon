import { Component, Input } from "@angular/core";
import { FormGroup } from "@angular/forms";

@Component({
    selector: "app-datetimeformat",
    templateUrl: "./datetimeformat.component.html",
    styleUrls: ["./datetimeformat.component.less"],
    standalone: false
})
export class DatetimeformatComponent {
    @Input() form: FormGroup;
}
