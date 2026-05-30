<script setup>

import {computed, onMounted, reactive, ref} from "vue";
import postService from "../../services/postService.js";
import categoryService from "../../services/categoryService.js";
import {useRouter} from "vue-router";
import Header from "../../components/Header.vue";
import {helpers, maxLength, required} from "@vuelidate/validators";
import {useVuelidate} from "@vuelidate/core";
import {useFileUpload} from "../../composables/useFileUpload.js";

const router = useRouter()

const writeForm = ref();

const pageMeta = reactive({
  categories: [],
});

const { fileState, addFileInput, handleFileChange } = useFileUpload({ maxFiles: 10, withPreview: true });

const formState = reactive({
  categoryId: '',
  title: '',
  content: '',
});

const rules = computed(() => ({
  categoryId: {
    required: helpers.withMessage('카테고리는 필수입니다', required),
  },
  title: {
    required: helpers.withMessage('제목은 필수입니다', required),
    maxLength: helpers.withMessage('제목은 100자 미만입니다', maxLength(100)),
  },
  content: {
    required: helpers.withMessage('내용은 필수입니다', required),
    maxLength: helpers.withMessage('내용은 4000자 미만입니다', maxLength(4000)),
  },
  uploadFiles: {
    type: helpers.withMessage('명시된 파일타입만 가능합니다.',(value) =>
        value.every(file => ['image/png', 'image/jpeg', 'image/gif'].includes(file.type))
    ),
    minLength: helpers.withMessage("최소 1개의 이미지를 업로드해야합니다", (value) =>
        value.length >= 1
    ),
    size: helpers.withMessage("파일용량은 1MB를 초과할 수 없습니다", (value) =>
        value.every(file => file.size < 1048576)
    ),
  }
}));

const validationTarget = computed(() => ({ ...formState, uploadFiles: fileState.uploadFiles }));
const v$ = useVuelidate(rules, validationTarget);

onMounted(()=>{
  getCategories();
});

const getCategories = async () => {
  try {
    const params = { boardType : "GALLERIES" }
    const response = await categoryService.getCategories({params});
    pageMeta.categories = response.data.payload;
  } catch (e) {
    // 에러 메세지는 axios interceptor에서 처리
  }
};

const createPost = async () => {
  const formValid = await v$.value.$validate();
  if(formValid){
    try {
      await postService.createPost("galleries", writeForm.value);
      await router.push({name: "Galleries"});
    } catch (e) {
      // 에러 메세지는 axios interceptor에서 처리
    }
  }
};

const cancel = () => {
  if (confirm("작성을 취소하시겠습니까?")) {
    router.push({name: "Galleries"});
  }
}
</script>

<template>
  <Header />
  <div class="min-h-screen bg-gray-50">
    <div class="container mx-auto px-4 py-8 max-w-3xl">

      <div class="mb-6">
        <h1 class="text-2xl font-bold text-gray-800">갤러리</h1>
        <p class="text-sm text-gray-500 mt-1">새 사진을 업로드해보세요</p>
      </div>

      <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
        <form ref="writeForm" class="space-y-5">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">분류</label>
            <select v-model="formState.categoryId" name="categoryId"
                    class="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-100">
              <option value="">분류 선택</option>
              <option v-for="category in pageMeta.categories" :key="category.id" :value="category.id">{{ category.name }}</option>
            </select>
            <span class="text-xs text-red-500 mt-1 block" v-if="v$.categoryId.$error">{{ v$.categoryId.$errors[0].$message }}</span>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">제목</label>
            <input v-model="formState.title" type="text" name="title"
                   class="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-100">
            <span class="text-xs text-red-500 mt-1 block" v-if="v$.title.$error">{{ v$.title.$errors[0].$message }}</span>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">내용</label>
            <textarea v-model="formState.content" name="content"
                      class="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm h-40 resize-none focus:outline-none focus:ring-2 focus:ring-blue-100"></textarea>
            <span class="text-xs text-red-500 mt-1 block" v-if="v$.content.$error">{{ v$.content.$errors[0].$message }}</span>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">이미지</label>
            <p class="text-xs text-gray-400 mb-2">jpg, gif, png · 최대 1MB</p>
            <div v-if="fileState.previewUrls.length > 0" class="flex flex-wrap gap-2 mb-3">
              <img v-for="url in fileState.previewUrls" :src="url" alt=""
                   class="w-20 h-20 object-cover rounded-lg border border-gray-100">
            </div>
            <div class="space-y-2">
              <div v-for="(f, index) in fileState.fileCount" :key="index">
                <input type="file" name="file" accept="image/png, image/gif, image/jpeg"
                       @change="handleFileChange($event, index)"
                       class="block w-full text-sm text-gray-500 border border-gray-200 rounded-lg file:mr-3 file:py-1.5 file:px-3 file:rounded-md file:border-0 file:text-xs file:bg-gray-100 hover:file:bg-gray-200 file:cursor-pointer">
              </div>
              <span class="text-xs text-red-500 block" v-if="v$.uploadFiles.$error">{{ v$.uploadFiles.$errors[0].$message }}</span>
              <button type="button" @click="addFileInput"
                      class="text-xs text-blue-500 hover:text-blue-700 font-medium transition-colors">
                + 이미지 추가
              </button>
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