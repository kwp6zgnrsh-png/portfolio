import {instance} from "../api/apiClient";

const homeService = {
    getHome: async () => {
        return await instance.get("/home");
    },
};

export default homeService;
