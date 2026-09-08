import { User, UserInfo } from "./user";

describe("User", () => {
	it("should create an instance", () => {
		expect(new User(true, new UserInfo())).toBeTruthy();
	});
});
