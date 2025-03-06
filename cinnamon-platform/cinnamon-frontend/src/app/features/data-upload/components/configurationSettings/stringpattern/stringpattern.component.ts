import { Component, Input } from "@angular/core";
import { FormGroup } from "@angular/forms";

@Component({
	selector: "app-stringpattern",
	templateUrl: "./stringpattern.component.html",
	styleUrls: ["./stringpattern.component.less"],
})
export class StringpatternComponent {
    @Input() form: FormGroup;
}
