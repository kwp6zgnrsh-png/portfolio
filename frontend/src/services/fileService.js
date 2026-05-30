import {instance} from "../api/apiClient";
import fileDownLoad from "js-file-download";

const fileService = {
    downloadFile: async (boardType,fileId, fileName) => {
        const response = await instance.get("/"+boardType+"/files/"+fileId,{responseType: 'blob'});
        fileDownLoad(response.data, fileName);
    },
    getImageUrl: (path, name, extension) => {
        return `/images/${path}${name}${extension}`;
    },
};

export default fileService;
