import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class TitleService {

  private pageTitle: String; 

  constructor() { 
    this.pageTitle = '<No page title set>'; 
  }

  setPageTitle(newTitle: String) {
    this.pageTitle = newTitle; 
  }

  getPageTitle(): String {
    return this.pageTitle; 
  }
}
