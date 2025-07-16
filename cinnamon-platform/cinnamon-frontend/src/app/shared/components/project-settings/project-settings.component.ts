import { Component, ElementRef, TemplateRef, ViewChild } from '@angular/core';
import { MatDialog } from "@angular/material/dialog";
import { UserService } from "src/app/shared/services/user.service";

/**
 * Component for the project settings.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'app-project-settings',
    standalone: false,
    templateUrl: './project-settings.component.html',
    styleUrl: './project-settings.component.less'
})
export class ProjectSettingsComponent {

    protected isMainOpen: boolean = true;
    protected deletionError: string | null = null;

    @ViewChild('projectSettingsDialog') private dialogWrap: TemplateRef<any>;
    @ViewChild('main') private main: ElementRef<HTMLDivElement>;
    @ViewChild('deletionConfirmation') private deletionConfirmation: ElementRef<HTMLDivElement>;

    public constructor(
        private readonly matDialog: MatDialog,
        private readonly userService: UserService,
    ) {
    }

    /**
     * Opens the dialog.
     */
    public open(): void {
        this.matDialog.open(this.dialogWrap, {
            width: '60%'
        });
    }

    /**
     * Returns the project name.
     * @protected
     */
    protected get projectName(): string {
        return this.userService.getUser().email;
    }

    /**
     * Opens the confirmation dialog for deleting the project.
     * @protected
     */
    protected openDeletionConfirmation(): void {
        this.deletionConfirmation.nativeElement.scrollIntoView();
        this.isMainOpen = false;
    }

    /**
     * Opens the main menu.
     * @protected
     */
    protected openMain(): void {
        this.main.nativeElement.scrollIntoView();
        this.isMainOpen = true;
    }

    /**
     * Deletes the project.
     * Displays a message if an error happens.
     *
     * @param projectName The name of the project for conformation.
     * @param password The password for confirmation.
     * @protected
     */
    protected deleteProject(projectName: string, password: string): void {
        this.deletionError = null;
        this.userService.delete(projectName, password).subscribe({
            next: () => {
                this.userService.logout('delete');
            },
            error: e => {
                this.deletionError = e.error.errorMessage;
            }
        });
    }

}
