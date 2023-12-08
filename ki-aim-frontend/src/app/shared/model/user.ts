export class User {
    constructor(
    public authenticated: boolean,
    public email: string,
    public token: string,
    ){}
}
