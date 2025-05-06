import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { AppConfig, AppConfigService } from "@shared/services/app-config.service";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { Observable, take } from "rxjs";

@Component({
    selector: 'app-file-upload',
    templateUrl: './file-upload.component.html',
    styleUrl: './file-upload.component.less',
    standalone: false,
})
export class FileUploadComponent implements OnInit {
    protected dataFile: File | null = null;
    protected errors = {
        large: false,
        type: false,
    };
    protected isDragging = false;

    protected appConfig$: Observable<AppConfig>;

    /**
     * Disables the file input.
     */
    @Input() public disabled: boolean = false;

    /**
     * Files accepted by the file input.
     */
    @Input() public accept: string = "";

    /**
     * Event emitted when a file is selected or dropped.
     */
    @Output() public input: EventEmitter<FileList | null> = new EventEmitter();

    public constructor(
        private readonly appConfigService: AppConfigService,
        private readonly errorHandlingService: ErrorHandlingService,
    ) {
    }

    public ngOnInit(): void {
        this.appConfig$ = this.appConfigService.appConfig$;
    }

    /**
     * Handles when a file is dropped on the component.
     * @param event The drag event.
     * @protected
     */
    protected onDragOver(event: DragEvent) {
        event.preventDefault();
        event.stopPropagation();

        if (this.disabled) {
            return;
        }

        this.isDragging = true;
    }

    /**
     * Handles when a file is no longer dragged over the component.
     * @param event The drag event.
     * @protected
     */
    protected onDragLeave(event: DragEvent) {
        event.preventDefault();
        event.stopPropagation();

        if (this.disabled) {
            return;
        }

        this.isDragging = false;
    }

    /**
     * Handles when a file is dropped on the component.
     * @param event The drag event.
     * @protected
     */
    protected onDrop(event: DragEvent) {
        event.preventDefault();
        event.stopPropagation();

        if (this.disabled) {
            return;
        }

        this.isDragging = false;

        const files = event.dataTransfer ? event.dataTransfer.files : null;
        this.handleInput(files);
    }

    /**
     * Handles when a file is selected by using the button.
     * @param event The input event.
     * @protected
     */
    protected onFileInput(event: Event) {
        event.preventDefault();
        event.stopPropagation();

        const files = (event.target as HTMLInputElement)?.files;
        this.handleInput(files);
    }

    protected formatMaxFileSize(maxFileSize: number): string {
        if (maxFileSize < 1024) {
            return maxFileSize + " byte";
        } else if (maxFileSize < 1024 * 1024) {
            return (maxFileSize / 1024).toFixed(2) + " kilobyte";
        } else if (maxFileSize < 1024 * 1024 * 1024) {
            return (maxFileSize / (1024 * 1024)).toFixed(2) + " megabyte";
        } else {
            return (maxFileSize / (1024 * 1024 * 1024)).toFixed(2) + " gigabyte";
        }
    }

    /**
     * Handles the file input.
     * @param files The files selected or dropped.
     * @private
     */
    private handleInput(files: FileList | null) {
        if (files && files.length > 0) {
            this.errors.large = false;
            this.errors.type = false;

            const file = files[0];
            this.dataFile = file;

            const fileExtension = this.getFileExtension(file);

            if (fileExtension == null || !this.accept.includes(fileExtension)) {
                this.errors.type = true;
                return;
            }

            this.appConfigService.appConfig$.pipe(
                take(1),
            ).subscribe({
                next: value => {
                    if (file.size > value.maxFileSize) {
                        this.errors.large = true;
                    } else {
                        this.input.emit(files);
                    }
                },
                error: error => {
                    this.errorHandlingService.addError(error, "Could not get app config.");
                }
            });
        }
    }

    /**
     * Extracts the file extension from the given file.
     * @param file The File
     * @return The file extension without `.`.
     * @private
     */
    private getFileExtension(file: File): string | undefined {
        return file.name.split(".").pop();
    }

}
