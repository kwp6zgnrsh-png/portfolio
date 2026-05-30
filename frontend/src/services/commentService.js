import {instance} from "../api/apiClient";

const commentService = {
    createComment: async (params) =>{
        return await instance.post("/comment", params);
    },
    deleteComment: async (commentId) =>{
        await instance.delete("/comment/"+commentId);
    },
};

export default commentService;
