import { Injectable } from "@angular/core";

@Injectable({
	providedIn: "root",
})
export class LoadingService {
    loading: boolean = false; 

	constructor() {}

    toggleLoading(){
        this.loading = !this.loading; 
    }

    getLoadingStatus(): boolean {
        return this.loading; 
    }

    setLoadingStatus(value: boolean) {
        this.loading = value; 
    }

}
