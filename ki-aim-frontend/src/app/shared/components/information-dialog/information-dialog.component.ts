import { Component, Inject } from "@angular/core";
import { MAT_DIALOG_DATA } from "@angular/material/dialog";

@Component({
	selector: "app-information-dialog",
	templateUrl: "./information-dialog.component.html",
	styleUrls: ["./information-dialog.component.less"],
})
export class InformationDialogComponent {
    constructor(@Inject(MAT_DIALOG_DATA) public data: DialogData) {}
}

interface DialogData {
    title: String; 
    content: String; 
}
