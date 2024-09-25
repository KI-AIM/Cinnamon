import { Component, Input, OnInit } from '@angular/core';
import * as d3 from 'd3';
import { HistogramData, HistogramEntry } from 'src/app/shared/model/visualization/histogram-entry';
import { DataConfigurationService } from 'src/app/shared/services/data-configuration.service';

@Component({
    selector: 'app-histogram',
    templateUrl: './histogram.component.html',
    styleUrls: ['./histogram.component.less'],
})
export class HistogramComponent implements OnInit {
    @Input() data: HistogramData; 
    private colorPalette: string[] = [
        "#6a40fd", "#e8505b", "#f9d56e", "#f9844a", "#f8961e", 
        "#43aa8b", "#577590", "#22d1ee", "#b5179e", "#560bad",
        "#ffd166", "#06d6a0", "#118ab2", "#073b4c", "#ef476f",
        "#ffc6ff", "#9bf6ff", "#a0c4ff", "#bdb2ff", "#ffadad",
        "#72efdd", "#80ffdb", "#d8f3dc", "#40916c", "#2a9d8f"
    ];
    private colorIndex: number = 0;

    constructor(public configurationService: DataConfigurationService) {}

    private drawBars(data: HistogramEntry): void {
        // Create the SVG canvas
        const margin = { top: 40, right: 20, bottom: 30, left: 40 };
        const width = 600 - margin.left - margin.right;
        const height = 400 - margin.top - margin.bottom;
        const animationTime = 500; 
        
        const svg = d3.select("#histogram-container").append("svg")
            .attr('width', width + margin.left + margin.right)
            .attr('height', height + margin.top + margin.bottom)
            .on('click', function() {
                d3.select(this).classed('enlarged', !d3.select(this).classed('enlarged'));
            })
            .append('g')
            .attr('transform', `translate(${margin.left}, ${margin.top})`)

        // Flatten data structure
        let dataset: any[] = [];
        data.columnData.forEach((value, key) => {
            dataset.push({
                category: key,
                value: value
            });
        });

        // Set the scales
        const x = d3.scaleBand()
            .range([0, width])
            .padding(0.1)
            .domain(dataset.map(d => d.category));

        const y = d3.scaleLinear()
            .range([height, 0])
            .domain([0, d3.max(dataset, d => d.value)]);

        // Create the bars
        svg.selectAll(".bar")
            .data(dataset)
            .enter().append("rect")
            .attr("class", "bar")
            .transition()
            .duration(animationTime)
            .attr("x", d => x(d.category) ?? "error")
            .attr("width", x.bandwidth())
            .attr("y", d => y(d.value))
            .attr("height", d => height - y(d.value))
            .attr("fill", (d, i) => this.colorPalette[this.colorIndex])
            .delay(function(d,i){return(i*100)}); 

        this.advanceColor();


        // Add the x-axis
        svg.append("g")
            .attr("transform", `translate(0, ${height})`)
            .call(d3.axisBottom(x));

        // Add the y-axis
        svg.append("g")
        .call(
            d3.axisLeft(y)
            .tickFormat(d3.format("d"))
            .ticks(data.getLargestNumber())
        );
    }

    private drawSmallBars(data: HistogramEntry, name: string): void {
        // Create the SVG canvas
        const margin = { top: 40, right: 20, bottom: 20, left: 20 };
        const width = 400 - margin.left - margin.right;
        const height = 200 - margin.top - margin.bottom;
        const animationTime = 500; 
        
        const svg = d3.select("#histogram-container").append("svg")
            .attr('width', width + margin.left + margin.right)
            .attr('height', height + margin.top + margin.bottom)
            .append('g')
            .attr('transform', `translate(${margin.left}, ${margin.top})`)

        // Flatten data structure
        let dataset: any[] = [];
        data.columnData.forEach((value, key) => {
            dataset.push({
                category: key,
                value: value
            });
        });

        // Set the scales
        const x = d3.scaleBand()
            .range([0, width])
            .padding(0.1)
            .domain(dataset.map(d => d.category));

        const y = d3.scaleLinear()
            .range([height, 0])
            .domain([0, d3.max(dataset, d => d.value)]);

        // Create the bars
        svg.selectAll(".bar")
            .data(dataset)
            .enter().append("rect")
            .attr("class", "bar")
            .transition()
            .duration(animationTime)
            .attr("x", d => x(d.category) ?? "error")
            .attr("width", x.bandwidth())
            .attr("y", d => y(d.value))
            .attr("height", d => height - y(d.value))
            .attr("fill", (d, i) => this.colorPalette[this.colorIndex])
            .delay(function(d,i){return(i*100)}); 

        this.advanceColor();


        // Add the x-axis
        svg.append("g")
            .attr("transform", `translate(0, ${height})`);

        // Add the y-axis
        svg.append("g")
        .call(
            d3.axisLeft(y)
            .tickFormat(d3.format("d"))
            .ticks(data.getLargestNumber() / 2)
        );

        svg.append("text")
            .attr("class", "x label")
            .attr("text-anchor", "center")
            .attr("x", (width - margin.left) / 2)
            .attr("y", height + margin.bottom)
            .text(name);
    }

    ngOnInit(): void {
        this.configurationService.getConfigurationNames().forEach(name => {
            this.drawSmallBars(this.data.getEntry(name as string)!!, name as string)
        });
    }

    private advanceColor(): void {
        this.colorIndex = (this.colorIndex + 1) % this.colorPalette.length; // Cycle through colors
    }
}
