import { Component } from "@angular/core";
import { DateFormatConfiguration } from "src/app/shared/model/date-format-configuration";

@Component({
	selector: "app-dateformat",
	templateUrl: "./dateformat.component.html",
	styleUrls: ["./dateformat.component.less"],
})
export class DateformatComponent {
    public dateformatConfiguration = new DateFormatConfiguration(); 

    getDateFormatConfiguration() {
        return this.dateformatConfiguration; 
    }

}
