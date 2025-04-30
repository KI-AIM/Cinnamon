import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
    selector: 'app-file-upload',
    templateUrl: './file-upload.component.html',
    styleUrl: './file-upload.component.less',
    standalone: false,
})
export class FileUploadComponent {
    protected dataFile: File | null = null;
    protected isDragging = false;

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

    /**
     * Handles the file input.
     * @param files The files selected or dropped.
     * @private
     */
    private handleInput(files: FileList | null) {
        if (files && files.length > 0) {
            this.dataFile = files[0];
            this.input.emit(files);
        }
    }

}
