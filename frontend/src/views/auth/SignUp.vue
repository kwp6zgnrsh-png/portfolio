<script setup>
import {computed, reactive, ref} from "vue";
import authService from "../../services/authService.js";
import Header from "../../components/Header.vue";
import {helpers, maxLength, minLength, required} from "@vuelidate/validators";
import {useVuelidate} from "@vuelidate/core";
import {useRouter} from "vue-router";

const router = useRouter();

const regForm = ref();

const member = reactive({
  memberId:'',
  memberPassword:'',
  confirmPassword:'',
  memberName:'',
});

const rules = computed(() => ({
  memberId: {
    required: helpers.withMessage('아이디는 필수입니다', required),
    patterns: helpers.withMessage("아이디는 a-z, 0-9, '-', '_'만 허용하며 4자 이상 12자리 미만입니다", (value) => /^[a-z0-9_-]{4,11}$/.test(value)),
  },
  memberPassword: {
    required: helpers.withMessage('비밀번호는 필수입니다', required),
    confirm: helpers.withMessage('확인용 비밀번호와 일치하지 않습니다',() => {
      return member.memberPassword === member.confirmPassword;
    }),
    contains: helpers.withMessage("비밀번호에 아이디는 포함될 수 없습니다", () => {
      return !member.memberPassword.includes(member.memberId);
    }),
    sequence: helpers.withMessage('비밀번호에 3번이상 연속된 문자를 사용할 수 없습니다', (value) => {
      for (let i = 0; i < value.length - 2; i++) {
        if (value[i] === value[i + 1] && value[i] === value[i + 2]) {
          return false;
        }
      }
      return true;
    })
  },
  confirmPassword: {
    required: helpers.withMessage('비밀번호 확인은 필수입니다', required),
  },
  memberName: {
    required: helpers.withMessage('이름은 필수입니다', required),
    minLength: helpers.withMessage("이름은 2글자 이상입니다", minLength(2)),
    maxLength: helpers.withMessage("이름은 5글자 미만입니다", maxLength(4)),
  }
}));

const v$ = useVuelidate(rules, member);

const validationState = reactive({
  isMemberIdValid : false,
})

const validateMemberId = async () =>{
  try {
    const response = await authService.validateMemberId(member);
    if(response.status === 200) {
      alert(response.data.message);
      validationState.isMemberIdValid = true;
    }
  } catch (e) {
    // 에러 메세지는 axios interceptor에서 처리
  }
}

const createMember = async () =>{
  if(!validationState.isMemberIdValid) {
    alert("아이디 중복확인을 해주세요");
    return;
  }

  const memberValid = await v$.value.$validate();
  if(memberValid) {
    try {
      await authService.createMember(member);
      await router.push({name:"Home"});
    } catch (e) {
      // 에러 메세지는 axios interceptor에서 처리
    }
  }
}
</script>

<template>
  <Header />
  <div class="min-h-screen bg-gray-50 flex items-start justify-center px-4 pt-20">
    <div class="w-full max-w-sm">

      <div class="text-center mb-8">
        <h1 class="text-2xl font-bold text-gray-800">회원가입</h1>
        <p class="text-sm text-gray-500 mt-1">새 계정을 만들어보세요</p>
      </div>

      <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
        <form ref="regForm" class="space-y-4">

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">아이디</label>
            <div class="flex gap-2">
              <input :readonly="validationState.isMemberIdValid" type="text" v-model="member.memberId"
                     placeholder="아이디 입력"
                     class="flex-1 border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-100">
              <button type="button" @click="validateMemberId"
                      class="px-3 py-2.5 text-sm text-gray-600 bg-gray-100 hover:bg-gray-200 rounded-lg transition-colors shrink-0">
                중복확인
              </button>
            </div>
            <span class="text-xs text-red-500 mt-1 block" v-if="v$.memberId.$error">{{ v$.memberId.$errors[0].$message }}</span>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">비밀번호</label>
            <input :disabled="!validationState.isMemberIdValid" type="password" v-model="member.memberPassword"
                   placeholder="비밀번호 입력"
                   class="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-100 disabled:bg-gray-50 disabled:text-gray-400">
            <span class="text-xs text-red-500 mt-1 block" v-if="v$.memberPassword.$error">{{ v$.memberPassword.$errors[0].$message }}</span>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">비밀번호 확인</label>
            <input :disabled="!validationState.isMemberIdValid" type="password" v-model="member.confirmPassword"
                   placeholder="비밀번호 재입력"
                   class="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-100 disabled:bg-gray-50 disabled:text-gray-400">
            <span class="text-xs text-red-500 mt-1 block" v-if="v$.confirmPassword.$error">{{ v$.confirmPassword.$errors[0].$message }}</span>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">이름</label>
            <input :disabled="!validationState.isMemberIdValid" type="text" v-model="member.memberName"
                   placeholder="이름 입력"
                   class="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-100 disabled:bg-gray-50 disabled:text-gray-400">
            <span class="text-xs text-red-500 mt-1 block" v-if="v$.memberName.$error">{{ v$.memberName.$errors[0].$message }}</span>
          </div>

          <button :disabled="!validationState.isMemberIdValid" type="button" @click="createMember"
                  class="w-full bg-blue-500 hover:bg-blue-600 text-white text-sm font-medium py-2.5 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed mt-2">
            회원가입
          </button>

        </form>
      </div>

    </div>
  </div>
</template>

<style scoped>

</style>