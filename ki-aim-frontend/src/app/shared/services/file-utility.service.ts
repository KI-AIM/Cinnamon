import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class FileUtilityService {

  constructor() { }

    public saveFile(fileData: Blob, fileName: string) {
        const anchor = document.createElement('a');
        anchor.href = URL.createObjectURL(fileData);
        anchor.download = fileName;
        document.body.appendChild(anchor);
        anchor.click();
        document.body.removeChild(anchor);
    }
}
