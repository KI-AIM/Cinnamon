import { Component, Input, TemplateRef, ViewChild } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";

/**
 * Dialog for providing help about an input field.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: "app-information-dialog",
    templateUrl: "./information-dialog.component.html",
    styleUrls: ["./information-dialog.component.less"],
    standalone: false,
})
export class InformationDialogComponent {

    /**
     * The title of the dialog.
     */
    @Input() public title!: String;

    /**
     * The dialog element.
     */
    @ViewChild("infoDialog") public dialogRef: TemplateRef<any>;

    public constructor(
        protected readonly matDialog: MatDialog
    ) {
    }

    /**
     * Handles a click event that opens the dialog and stops the event to prevent triggering the input field.
     * @param event The click event.
     */
    public handleClick(event: Event): void {
        this.matDialog.open(this.dialogRef);
        event.stopPropagation();
    }

}
