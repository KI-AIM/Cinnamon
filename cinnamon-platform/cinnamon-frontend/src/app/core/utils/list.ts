export class List<T> {
    private items: Array<T>;

    constructor(items?: Array<T>) {
        if (items !== null && items !== undefined) {
            this.items = items;
        } else {
            this.items = [];
        }
    }


    size(): number {
        return this.items.length;
    }

    add(value: T): void {
        this.items.push(value);
    }

    get(index: number): T {
        return this.items[index];
    }

    getAll(): Array<T> {
        return this.items;
    }

    contains(value: T): boolean {
        return this.items.includes(value);
    }

    remove(value: T): void {
        var index = this.items.indexOf(value, 0);
        if (index > -1) {
            this.items.splice(index, 1);
        }
    }

    clear(): void {
        this.items.length = 0;
    }
}
