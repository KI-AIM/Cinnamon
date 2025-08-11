import { Directive, ElementRef, HostListener } from '@angular/core';
import { MatExpansionPanel } from "@angular/material/expansion";

/**
 * Overwrites the overflow handling of MatExpansionPanels
 * Sets `overflow: visible` for absolute positioned elements inside the panels.
 * During the transition, the overflow is hidden for a smooth transition.
 *
 * @author Daniel Preciado-Marquez
 */
@Directive({
    selector: '[appExpansionPanelOverflow]',
    standalone: false,
})
export class ExpansionPanelOverflowDirective {

    constructor(
        private readonly elRef: ElementRef,
        panel: MatExpansionPanel,
    ) {
        panel.afterCollapse.subscribe(() => this.afterCollapse());
        panel.afterExpand.subscribe(() => this.afterExpand());

        elRef.nativeElement.classList.add('cinnamon-overflow-handling');
    }

    /**
     * Temporarily hides the overflow for mat-expansion panels.
     * And shows the content of the panel if it is currently expanded.
     *
     * @private
     */
    @HostListener('click')
    public beforeCollapseExpand() {
        this.elRef.nativeElement.classList.add('cinnamon-overflow-hidden');
        if (this.elRef.nativeElement.classList.contains('mat-expanded')) {
            this.elRef.nativeElement.querySelector('.mat-expansion-panel-body').style.display = '';
        }
    }

    /**
     * Show the overflow for mat-expansion panels and hides the content of the panel.
     *
     * @private
     */
    private afterCollapse() {
        this.elRef.nativeElement.classList.remove('cinnamon-overflow-hidden');
        this.elRef.nativeElement.querySelector('.mat-expansion-panel-body').style.display = 'none';
    }

    /**
     * Shows the overflow for mat-expansion panels.
     *
     * @private
     */
    private afterExpand() {
        this.elRef.nativeElement.classList.remove('cinnamon-overflow-hidden');
    }

}
