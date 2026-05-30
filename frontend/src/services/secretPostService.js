import {instance} from "../api/apiClient";

const secretPostService = {
    verifyMySecretPost: async (boardType, boardId) => {
        return await instance.post("/"+boardType+"/"+boardId+"/verifyMySecretPost");
    },
    verifySecretPostPassword: async (boardType, boardId, params) => {
        return await instance.post("/"+boardType+"/"+boardId+"/verifySecretPostPassword", params);
    },
};

export default secretPostService;
