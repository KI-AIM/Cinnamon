import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class FileUtilityService {

  constructor() { }

  public saveYamlFile(fileData: string, fileName: string) {
    const blob = new Blob([fileData], { type: "text/yaml" });
    this.saveFile(blob, fileName);
  }

  public saveFile(fileData: Blob, fileName: string) {
    const anchor = document.createElement('a');
    anchor.href = URL.createObjectURL(fileData);
    anchor.download = fileName;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
  }
}
