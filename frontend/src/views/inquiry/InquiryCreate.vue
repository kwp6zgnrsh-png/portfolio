<script setup>

import {computed, reactive, ref, watch} from "vue";
import postService from "../../services/postService.js";
import {useRouter} from "vue-router";
import Header from "../../components/Header.vue";
import {helpers, maxLength, required} from "@vuelidate/validators";
import {useVuelidate} from "@vuelidate/core";

const router = useRouter();

const writeForm = ref();

const formState = reactive({
  content: '',
  title: '',
  isSecret: false,
  secretPassword: null,
});

const rules = computed(() => ({
  title: {
    required: helpers.withMessage('제목은 필수입니다', required),
    maxLength: helpers.withMessage('제목은 100자 미만입니다', maxLength(100)),
  },
  content: {
    required: helpers.withMessage('내용은 필수입니다', required),
    maxLength: helpers.withMessage('내용은 4000자 미만입니다', maxLength(4000)),
  },
  secretPassword: {
    requiredIfSecret: helpers.withMessage("비공개 시 비밀번호는 필수입니다", (value) =>
        !formState.isSecret || (value !== null && value !== '')
    ),
    patterns: helpers.withMessage("비밀번호는 4자리 숫자여야합니다", (value) =>
        value === null || value === '' || /^\d{4}$/.test(value)
    ),
  }
}));

const v$ = useVuelidate(rules, formState);

// 비밀번호 숫자 4자리인지 검증 해야함
const createPost = async () => {
  const formValid = await v$.value.$validate();
  if(formValid){
    try{
      await postService.createPost("inquiries", writeForm.value);
      await router.push({name: "Inquiries"});
    } catch (e) {
      // 에러 메세지는 axios interceptor에서 처리
    }
  }
};

const cancel = () => {
  if (confirm("작성을 취소하시겠습니까?")) {
    router.push({name: "Inquiries"});
  }
}

watch(() => formState.isSecret, (newValue) => {
  if (!newValue) {
    formState.secretPassword = null;
  }
});

</script>

<template>
  <Header />
  <div class="min-h-screen bg-gray-50">
    <div class="container mx-auto px-4 py-8 max-w-3xl">

      <div class="mb-6">
        <h1 class="text-2xl font-bold text-gray-800">문의게시판</h1>
        <p class="text-sm text-gray-500 mt-1">궁금한 점을 문의해보세요</p>
      </div>

      <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
        <form ref="writeForm" class="space-y-5">

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">제목</label>
            <input name="title" type="text" v-model="formState.title"
                   class="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-100">
            <span class="text-xs text-red-500 mt-1 block" v-if="v$.title.$error">{{ v$.title.$errors[0].$message }}</span>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">내용</label>
            <textarea name="content" v-model="formState.content"
                      class="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm h-60 resize-none focus:outline-none focus:ring-2 focus:ring-blue-100"></textarea>
            <span class="text-xs text-red-500 mt-1 block" v-if="v$.content.$error">{{ v$.content.$errors[0].$message }}</span>
          </div>

          <div class="border border-gray-100 rounded-lg p-4 space-y-4">
            <div class="flex items-center gap-2">
              <input id="isSecret" name="isSecret" type="checkbox" v-model="formState.isSecret"
                     class="w-4 h-4 text-blue-500 rounded border-gray-300 cursor-pointer">
              <label for="isSecret" class="text-sm font-medium text-gray-700 cursor-pointer">비공개 글</label>
            </div>
            <div v-if="formState.isSecret">
              <input name="secretPassword" type="password" v-model="formState.secretPassword"
                     maxlength="4" placeholder="비밀번호 4자리 숫자"
                     class="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-100">
              <span class="text-xs text-red-500 mt-1 block" v-if="v$.secretPassword.$error">{{ v$.secretPassword.$errors[0].$message }}</span>
            </div>
          </div>

          <div class="flex justify-end gap-2 pt-2 border-t border-gray-100">
            <button type="button" @click="cancel"
                    class="px-5 py-2 text-sm text-gray-600 bg-gray-100 hover:bg-gray-200 rounded-lg transition-colors">
              취소
            </button>
            <button type="button" @click="createPost"
                    class="px-5 py-2 text-sm text-white bg-blue-500 hover:bg-blue-600 rounded-lg transition-colors">
              등록
            </button>
          </div>
        </form>
      </div>

    </div>
  </div>
</template>

<style scoped>
</style>