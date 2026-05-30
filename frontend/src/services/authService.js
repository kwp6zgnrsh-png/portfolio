import {instance} from "../api/apiClient";

const authService = {
    login: async (member) =>{
        return await instance.post("/login",member);
    },
    logout: async () => {
        await instance.post("/logout");
    },
    validateMemberId: async (member) => {
        return await instance.post("/validate-id", member);
    },
    createMember: async (member) => {
        await instance.post("/sign-up", member);
    },
};

export default authService;
