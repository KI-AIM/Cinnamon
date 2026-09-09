import { Component, Input, ChangeDetectionStrategy } from "@angular/core";
import { FormGroup } from "@angular/forms";

@Component({
    selector: "app-dateformat",
    templateUrl: "./dateformat.component.html",
    styleUrls: ["./dateformat.component.less"],
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
export class DateformatComponent {
    @Input() form: FormGroup;
}
