<script setup>
import {reactive} from "vue";
import authService from "../../services/authService.js";
import router from "../../router/index.js";
import {useAppStore} from "../../store/index.js";
import Header from "../../components/Header.vue";

const member = reactive({
  memberId:'',
  memberPassword:'',
});

const loginMember = async () => {
  try {
    const response = await authService.login(member);
    const payload = response.data.payload;

    useAppStore().setMemberName(payload.memberName);
    useAppStore().setMemberId(payload.memberId);

    if(useAppStore().getRedirectPath) {
      await router.push(useAppStore().getRedirectPath);
    } else {
      await router.push({name: "Home"});
    }
  } catch (e) {
    // 에러 메세지는 axios interceptor에서 처리
  }
};

const goToSignUp = () =>{
  router.push({name:"SignUp"});
};

</script>

<template>
  <Header />
  <div class="min-h-screen bg-gray-50 flex items-start justify-center px-4 pt-20">
    <div class="w-full max-w-sm">

      <div class="text-center mb-8">
        <h1 class="text-2xl font-bold text-gray-800">로그인</h1>
        <p class="text-sm text-gray-500 mt-1">계정에 로그인하세요</p>
      </div>

      <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-6 space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">아이디</label>
          <input type="text" v-model="member.memberId" placeholder="아이디를 입력하세요"
                 @keyup.enter="loginMember"
                 class="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-100">
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">비밀번호</label>
          <input type="password" v-model="member.memberPassword" placeholder="비밀번호를 입력하세요"
                 @keyup.enter="loginMember"
                 class="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-100">
        </div>

        <button @click="loginMember"
                class="w-full bg-blue-500 hover:bg-blue-600 text-white text-sm font-medium py-2.5 rounded-lg transition-colors mt-2">
          로그인
        </button>

        <div class="text-center pt-2 border-t border-gray-100">
          <span class="text-sm text-gray-400">계정이 없으신가요?</span>
          <button @click="goToSignUp"
                  class="text-sm text-blue-500 hover:text-blue-700 font-medium ml-1 transition-colors">
            회원가입
          </button>
        </div>
      </div>

    </div>
  </div>
</template>

<style scoped>

</style>