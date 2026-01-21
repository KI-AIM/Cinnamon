import { Component, Input, OnInit } from '@angular/core';

/**
 * Section of the information dialog.
 * Can be used directly or by extending this component.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
  selector: 'info-part',
  standalone: false,
  templateUrl: './information-dialog-part.component.html',
  styleUrl: './information-dialog-part.component.less'
})
export class InformationDialogPartComponent implements OnInit {
    /**
     * Title of the section displayed above the content.
     * Used if the function {@link #getTitle} returns null.
     */
    @Input() public title: string = "";

    /**
     * Title of the section displayed above the content.
     * @protected
     */
    protected _title: string = "";

    public ngOnInit(): void {
        this._title = this.getTitle() ?? this.title;
    }

    /**
     * Defines the section title.
     * If it returns null, the value of {@link #title} is used.
     * Can be used by child components to overwrite the title.
     *
     * @protected
     */
    protected getTitle(): string | null {
        return null;
    }
}
