import { Component, Input } from "@angular/core";
import { FormGroup } from "@angular/forms";

@Component({
	selector: "app-dateformat",
	templateUrl: "./dateformat.component.html",
	styleUrls: ["./dateformat.component.less"],
})
export class DateformatComponent {
    @Input() form: FormGroup;
}
