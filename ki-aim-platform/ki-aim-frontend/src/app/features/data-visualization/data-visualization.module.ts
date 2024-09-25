import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HistogramComponent } from './components/histogram/histogram.component';
import { DataInspectionComponent } from './pages/data-inspection/data-inspection.component';
import { SharedModule } from 'src/app/shared/shared.module';

@NgModule({
    imports: [CommonModule, SharedModule],
    declarations: [HistogramComponent, DataInspectionComponent],
})
export class DataVisualizationModule {}
