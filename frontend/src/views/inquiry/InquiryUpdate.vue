<script setup>

import {computed, onMounted, reactive, watch} from "vue";
import {useRoute, useRouter} from "vue-router";
import Header from "../../components/Header.vue";
import {helpers, maxLength, required} from "@vuelidate/validators";
import {useVuelidate} from "@vuelidate/core";
import postService from "../../services/postService.js";

const router = useRouter();
const route = useRoute();

onMounted(()=>{
  getPost();
});

const form = reactive({
  id: 0,
  content: '',
  title: '',
  isSecret: false,
  secretPassword: null,
  version: null,
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
    patterns: helpers.withMessage("새 비밀번호는 4자리 숫자여야합니다", (value) =>
        value === null || value === '' || /^\d{4}$/.test(value)
    ),
  }
}));

const v$ = useVuelidate(rules, form);

const getPost = async () => {
  try {
    const boardId = route.params.id;
    const response = await postService.getUpdatePost("inquiries", boardId);
    setFormData(response.data.payload.inquiryUpdate);
  } catch (e) {
    // 에러 메세지는 axios interceptor에서 처리
  }
};

const setFormData = (data) => {
  form.id = data.id;
  form.version = data.version;
  form.content = data.content;
  form.title = data.title;
  form.isSecret = data.isSecret;
};

const createForm = () => {
  const formData = new FormData();
  formData.append('title', form.title);
  formData.append('content', form.content);
  formData.append('isSecret', form.isSecret);
  formData.append('secretPassword', form.secretPassword ?? '');
  formData.append('version', String(form.version));
  return formData;
}

const update = async () => {
  const formValid = await v$.value.$validate();
  if(formValid){
    try {
      await postService.updatePost("inquiries", route.params.id, createForm());
      await router.push({
        name: "InquiryDetail",
        query: route.query,
        params: { id: form.id },
        state: { fromInquiryRoute: history.state?.fromInquiryRoute }
      });
    } catch (e) {
      // 에러 메세지는 axios interceptor에서 처리
    }
  }
};

const cancel = () => {
  if(confirm("수정을 취소하시겠습니까?")){
    router.go(-1);
  }
}

watch(() => form.isSecret, (newValue) => {
  if (!newValue) {
    form.secretPassword = null;
  }
});
</script>

<template>
  <Header />
  <div class="min-h-screen bg-gray-50">
    <div class="container mx-auto px-4 py-8 max-w-3xl">

      <div class="mb-6">
        <h1 class="text-2xl font-bold text-gray-800">문의게시판</h1>
        <p class="text-sm text-gray-500 mt-1">게시글을 수정합니다</p>
      </div>

      <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
        <form class="space-y-5">

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">제목</label>
            <input type="text" v-model="form.title"
                   class="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-100">
            <span class="text-xs text-red-500 mt-1 block" v-if="v$.title.$error">{{ v$.title.$errors[0].$message }}</span>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">내용</label>
            <textarea v-model="form.content"
                      class="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm h-60 resize-none focus:outline-none focus:ring-2 focus:ring-blue-100"></textarea>
            <span class="text-xs text-red-500 mt-1 block" v-if="v$.content.$error">{{ v$.content.$errors[0].$message }}</span>
          </div>

          <div class="border border-gray-100 rounded-lg p-4 space-y-4">
            <div class="flex items-center gap-2">
              <input id="isSecret" type="checkbox" v-model="form.isSecret"
                     class="w-4 h-4 text-blue-500 rounded border-gray-300 cursor-pointer">
              <label for="isSecret" class="text-sm font-medium text-gray-700 cursor-pointer">비공개 글</label>
            </div>
            <div v-if="form.isSecret" class="space-y-2">
              <p class="text-xs text-gray-500">
                현재 비밀글입니다. 비밀번호를 바꾸려면 새 비밀번호를 입력하세요. 비워두면 기존 비밀번호를 유지합니다.
              </p>
              <input type="password" v-model="form.secretPassword"
                     maxlength="4" placeholder="변경할 때만 새 비밀번호 4자리 입력"
                     class="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-100">
              <span class="text-xs text-red-500 mt-1 block" v-if="v$.secretPassword.$error">{{ v$.secretPassword.$errors[0].$message }}</span>
            </div>
          </div>

          <div class="flex justify-end gap-2 pt-2 border-t border-gray-100">
            <button type="button" @click="cancel"
                    class="px-5 py-2 text-sm text-gray-600 bg-gray-100 hover:bg-gray-200 rounded-lg transition-colors">
              취소
            </button>
            <button type="button" @click="update"
                    class="px-5 py-2 text-sm text-white bg-blue-500 hover:bg-blue-600 rounded-lg transition-colors">
              수정
            </button>
          </div>
        </form>
      </div>

    </div>
  </div>
</template>

<style scoped>
</style>
