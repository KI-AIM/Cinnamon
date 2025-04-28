import { Injectable } from '@angular/core';
import { MatAccordion } from "@angular/material/expansion";

@Injectable({
  providedIn: 'root'
})
export class WorkstepService {

    public accordion: MatAccordion | null = null;

  constructor() { }
}
