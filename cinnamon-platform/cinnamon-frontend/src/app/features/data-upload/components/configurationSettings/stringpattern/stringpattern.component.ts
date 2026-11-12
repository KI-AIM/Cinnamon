import { Component, Input, ChangeDetectionStrategy } from "@angular/core";
import { FormGroup } from "@angular/forms";

@Component({
    selector: "app-stringpattern",
    templateUrl: "./stringpattern.component.html",
    styleUrls: ["./stringpattern.component.less"],
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
export class StringpatternComponent {
    @Input() form: FormGroup;
}
