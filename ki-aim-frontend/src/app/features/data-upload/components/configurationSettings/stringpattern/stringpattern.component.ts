import { Component } from "@angular/core";
import { StringPatternConfiguration } from "src/app/shared/model/string-pattern-configuration";

@Component({
	selector: "app-stringpattern",
	templateUrl: "./stringpattern.component.html",
	styleUrls: ["./stringpattern.component.less"],
})
export class StringpatternComponent {
    public stringpatternConfiguration = new StringPatternConfiguration(); 

    getStringPatternConfiguration() {
        return this.stringpatternConfiguration; 
    }
}
