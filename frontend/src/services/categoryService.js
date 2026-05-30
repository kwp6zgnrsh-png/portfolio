import {instance} from "../api/apiClient";

const categoryService = {
    getCategories: async (params) =>{
        return await instance.get("/categories",params);
    },
};

export default categoryService;
