import axios from "axios";
import { useAppStore } from "../store/index.js";

export const instance = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL,
    withCredentials: true,
});

instance.interceptors.response.use(response => response, async error => {
    if (error.response) {
        if (error.response.status === 401) {
            alert("권한이 없습니다.");
            useAppStore().setMemberName('');
            useAppStore().setMemberId('');
            const { default: router } = await import('../router/index.js');
            await router.push('/login');
            return Promise.reject(error);
        }
        if(error.response.data instanceof Blob){
            const text = await error.response.data.text();
            const data = JSON.parse(text);
            alert(data?.message ?? '오류가 발생했습니다.');
        }else {
            alert(error.response?.data?.message ?? '오류가 발생했습니다.');
        }
    } else {
        alert('네트워크 오류가 발생했습니다. 연결을 확인해 주세요.');
    }
    return Promise.reject(error);
});