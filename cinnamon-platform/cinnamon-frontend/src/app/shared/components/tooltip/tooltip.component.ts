import { Component, ElementRef, Input, OnInit, ViewChild } from '@angular/core';

/**
 * Custom tooltip that allows HTML content.
 * Must be placed inside a div with `position: relative`.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
  selector: 'app-tooltip',
  standalone: false,
  templateUrl: './tooltip.component.html',
  styleUrl: './tooltip.component.less'
})
export class TooltipComponent implements OnInit {

    /**
     * The target div for listening to hover events.
     */
    @Input() public target!: HTMLDivElement;

    @ViewChild('tooltip') private tooltip!: ElementRef;
    @ViewChild('tooltipArrow') private tooltipArrow!: ElementRef;

    public ngOnInit(): void {
        this.target.addEventListener("mouseenter", () => this.openTooltip());
        this.target.addEventListener("mouseleave", () => this.closeTooltip());
    }

    /**
     * Opens the confidence tooltip.
     * Modifies the position to ensure the tooltip is in view.
     * @private
     */
    private openTooltip(): void {
        this.tooltip.nativeElement.classList.add("shown");

        const rect = this.tooltip.nativeElement.getBoundingClientRect();
        const maxArrowOffset = (rect.height - 17) / 2;
        const topOffset = 65 - rect.top;
        const bottomOffset = (window.outerHeight - 125) - rect.bottom;

        if (topOffset > 0) {
            this.tooltip.nativeElement.style.transform = `translateY(-50%) translateY(${topOffset}px)`;
            this.tooltipArrow.nativeElement.style.transform = `translateY(-${Math.min(maxArrowOffset, topOffset)}px)`;
        } else if (bottomOffset < 0) {
            this.tooltip.nativeElement.style.transform = `translateY(-50%) translateY(${bottomOffset}px)`;
            this.tooltipArrow.nativeElement.style.transform = `translateY(${Math.min(maxArrowOffset, -bottomOffset)}px)`;
        }
    }

    /**
     * Closes the tooltip and resets its position, so the translation can be calculated correctly when opening it again.
     * @private
     */
    private closeTooltip(): void {
        this.tooltip.nativeElement.classList.remove("shown");
        this.tooltip.nativeElement.style.transform = `translateY(-50%)`;
        this.tooltipArrow.nativeElement.style.transform = "";
    }

}
