<script setup>

import Header from "../../components/Header.vue";
import {reactive} from "vue";
import {useRoute, useRouter} from "vue-router";
import secretPostService from "../../services/secretPostService.js";

const router = useRouter();
const route = useRoute();

const form = reactive({
  password: "",
})

const verifyPassword = async () => {
  try {
    const boardId = route.params.id;
    const params = { password: form.password };
    const response = await secretPostService.verifySecretPostPassword("inquiries", boardId, params);
    if(response.data.message === "본인 확인"){
      await router.push({
        name:"InquiryDetail",
        params: { id: boardId },
        query: route.query,
        state: { fromInquiryRoute: history.state?.fromInquiryRoute }
      });
    }else {
      alert("비밀번호가 일치하지 않습니다.");
    }
  } catch (e) {
    // 에러 메세지는 axios interceptor에서 처리
  }
}


</script>

<template>
  <Header />
  <div class="min-h-screen bg-gray-50 flex items-start justify-center px-4 pt-20">
    <div class="w-full max-w-sm">

      <div class="text-center mb-8">
        <h1 class="text-2xl font-bold text-gray-800">비밀글</h1>
        <p class="text-sm text-gray-500 mt-1">비밀번호를 입력해 주세요</p>
      </div>

      <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-6 space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">비밀번호</label>
          <input type="password" v-model="form.password" placeholder="비밀번호 입력"
                 maxlength="4" @keyup.enter="verifyPassword"
                 class="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-100">
        </div>
        <button @click="verifyPassword"
                class="w-full bg-blue-500 hover:bg-blue-600 text-white text-sm font-medium py-2.5 rounded-lg transition-colors">
          확인
        </button>
      </div>

    </div>
  </div>
</template>

<style scoped>

</style>
