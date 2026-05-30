<script setup>
import Header from "../../components/Header.vue";
import postService from "../../services/postService.js";
import categoryService from "../../services/categoryService.js";
import {computed, onMounted, reactive, ref} from "vue";
import {required, maxLength, helpers} from '@vuelidate/validators'
import { useVuelidate } from '@vuelidate/core'
import {useRouter} from "vue-router";
import {useFileUpload} from "../../composables/useFileUpload.js";

const router = useRouter();

const writeForm = ref();

const pageMeta = reactive({
  categories: [],
});

const { fileState, addFileInput, handleFileChange } = useFileUpload({ maxFiles: 11 });

const formState = reactive({
  categoryId: '',
  title: '',
  content: ''
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
}));

const v$ = useVuelidate(rules, formState);

onMounted(()=>{
  getCategories();
});

const getCategories = async () => {
  try {
    const params = { boardType : "BOARDS" }
    const response = await categoryService.getCategories({ params });
    setCategories(response.data.payload);
  } catch (e) {
    // 에러 메세지는 axios interceptor에서 처리
  }
}

const setCategories = (data) => {
  pageMeta.categories = data;
}

const createPost = async () => {
  const formValid = await v$.value.$validate();
  if(formValid){
    try {
      await postService.createPost("boards", writeForm.value);
      await router.push({ name: "FreeBoards" });
    } catch (e) {
      // 에러 메세지는 axios interceptor에서 처리
    }
  }
}

const cancel = () => {
  if (confirm("작성을 취소하시겠습니까?")) {
    router.push({name: "FreeBoards"});
  }
}
</script>

<template>
  <Header />
  <div class="min-h-screen bg-gray-50">
    <div class="container mx-auto px-4 py-8 max-w-3xl">

      <div class="mb-6">
        <h1 class="text-2xl font-bold text-gray-800">자유게시판</h1>
        <p class="text-sm text-gray-500 mt-1">새 글을 작성해보세요</p>
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
                   class="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-100"/>
            <span class="text-xs text-red-500 mt-1 block" v-if="v$.title.$error">{{ v$.title.$errors[0].$message }}</span>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">내용</label>
            <textarea v-model="formState.content" name="content"
                      class="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm h-60 resize-none focus:outline-none focus:ring-2 focus:ring-blue-100"/>
            <span class="text-xs text-red-500 mt-1 block" v-if="v$.content.$error">{{ v$.content.$errors[0].$message }}</span>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">첨부</label>
            <p class="text-xs text-gray-400 mb-2">jpg, gif, png, zip · 최대 2MB</p>
            <div class="space-y-2">
              <input type="file" name="file" v-for="(f, index) in fileState.fileCount" :key="index"
                     @change="handleFileChange($event, index)"
                     accept="application/zip, image/jpeg, image/gif, image/png"
                     class="block w-full text-sm text-gray-500 border border-gray-200 rounded-lg file:mr-3 file:py-1.5 file:px-3 file:rounded-md file:border-0 file:text-xs file:bg-gray-100 hover:file:bg-gray-200 file:cursor-pointer">
              <button type="button" @click="addFileInput"
                      class="text-xs text-blue-500 hover:text-blue-700 font-medium transition-colors">
                + 파일 추가
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