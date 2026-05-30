import {instance} from "../api/apiClient";

const postService = {
    getPostList: async (path, params) =>{
        return await instance.get(path, params);
    },
    createPost: async (boardType, params) =>{
        await instance.post("/"+boardType,params);
    },
    getPostDetail: async (boardType, boardId) =>{
        const url = "/"+boardType+"/"+boardId;
        return await instance.get(url);
    },
    updatePost: async (boardType, boardId, params) => {
        await instance.put("/"+boardType+"/"+boardId, params);
    },
    getUpdatePost: async (boardType, boardId) => {
        return await instance.get(boardType+"/update/"+boardId);
    },
    deletePost: async (boardType, boardId) => {
        await instance.delete("/"+boardType+"/"+boardId);
    },
};

export default postService;
