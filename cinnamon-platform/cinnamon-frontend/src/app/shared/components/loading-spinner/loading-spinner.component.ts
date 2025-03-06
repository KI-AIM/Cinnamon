import { Component } from "@angular/core";
import { LoadingService } from "../../services/loading.service";

@Component({
	selector: "app-loading-spinner",
	templateUrl: "./loading-spinner.component.html",
	styleUrls: ["./loading-spinner.component.less"],
})
export class LoadingSpinnerComponent {
    constructor(public loadingService: LoadingService) {
        
    }

}
