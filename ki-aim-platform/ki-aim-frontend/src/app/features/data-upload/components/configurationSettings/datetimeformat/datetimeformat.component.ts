import { Component } from "@angular/core";
import { DateTimeFormatConfiguration } from "src/app/shared/model/date-time-format-configuration";

@Component({
	selector: "app-datetimeformat",
	templateUrl: "./datetimeformat.component.html",
	styleUrls: ["./datetimeformat.component.less"],
})
export class DatetimeformatComponent {
    public dateTimeFormatConfiguration = new DateTimeFormatConfiguration(); 

    getDateTimeFormatConfiguration() {
        return this.dateTimeFormatConfiguration; 
    }
}
