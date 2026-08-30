<script setup>

import {computed, onMounted, reactive, ref} from "vue";
import postService from "../../services/postService.js";
import categoryService from "../../services/categoryService.js";
import fileService from "../../services/fileService.js";
import {useRoute, useRouter} from "vue-router";
import Header from "../../components/Header.vue";
import {helpers, maxLength, required} from "@vuelidate/validators";
import {useVuelidate} from "@vuelidate/core";
import {useFileUpload} from "../../composables/useFileUpload.js";

const router = useRouter();
const route = useRoute();

const writeForm = ref();

const postMeta = reactive({
  id: 0,
  version: null,
  categories: [],
});

const { fileState, addFileInput, deleteExistingFile, setExistingFiles } = useFileUpload({ maxFiles: 10 });

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
}));

const v$ = useVuelidate(rules, formState);

onMounted(()=>{
  getCategories();
  getPost();
})

const getCategories = async () => {
  try {
    const params = { boardType : "BOARDS" }
    const response = await categoryService.getCategories({params});
    setCategories(response.data.payload);
  } catch (e) {}
};

const setCategories = (data) => {
  postMeta.categories = data;
}

const getPost = async () => {
  try {
    const boardId = route.params.id;
    const response = await postService.getUpdatePost("boards", boardId);
    setPostData(response.data.payload);
  } catch (e) {}
}

const setPostData = (data) => {
  postMeta.id = data.freeBoardUpdate.id;
  postMeta.version = data.freeBoardUpdate.version;
  formState.categoryId = data.freeBoardUpdate.categoryId;
  formState.title = data.freeBoardUpdate.title;
  formState.content = data.freeBoardUpdate.content;
  setExistingFiles(data.fileList);
}

const downloadFile = async (boardType,fileId, fileName) => {
  await fileService.downloadFile(boardType, fileId, fileName);
}

const updatePost = async () => {
  try {
    const formValid = await v$.value.$validate();
    if(formValid){
      const formData = createPostFormData();
      await postService.updatePost("boards", route.params.id, formData);
      await router.push({ name:"FreeBoardDetail", query: route.query, params: { id: postMeta.id } });
    }
  } catch (e) {}
}

const createPostFormData = () => {
  const formData = new FormData(writeForm.value);
  formData.append("version", String(postMeta.version));
  fileState.removeFiles.forEach(item => formData.append("deleteFiles", item));
  return formData;
}

const cancel = () => {
  if(confirm("수정을 취소하시겠습니까?")){
    router.go(-1);
  }
}
</script>

<template>
  <Header />
  <div class="min-h-screen bg-gray-50">
    <div class="container mx-auto px-4 py-8 max-w-3xl">

      <div class="mb-6">
        <h1 class="text-2xl font-bold text-gray-800">자유게시판</h1>
        <p class="text-sm text-gray-500 mt-1">게시글을 수정합니다</p>
      </div>

      <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
        <form ref="writeForm" class="space-y-5">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">분류</label>
            <select name="categoryId" v-model="formState.categoryId"
                    class="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-100">
              <option value="">분류 선택</option>
              <option v-for="category in postMeta.categories" :key="category.id" :value="category.id">{{ category.name }}</option>
            </select>
            <span class="text-xs text-red-500 mt-1 block" v-if="v$.categoryId.$error">{{ v$.categoryId.$errors[0].$message }}</span>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">제목</label>
            <input type="text" name="title" v-model="formState.title"
                   class="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-100">
            <span class="text-xs text-red-500 mt-1 block" v-if="v$.title.$error">{{ v$.title.$errors[0].$message }}</span>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">내용</label>
            <textarea name="content" v-model="formState.content"
                      class="w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm h-60 resize-none focus:outline-none focus:ring-2 focus:ring-blue-100"></textarea>
            <span class="text-xs text-red-500 mt-1 block" v-if="v$.content.$error">{{ v$.content.$errors[0].$message }}</span>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">첨부</label>
            <p class="text-xs text-gray-400 mb-2">jpg, gif, png, zip · 최대 2MB</p>
            <div class="space-y-2">
              <div v-for="(file, index) in fileState.files" :key="file.id"
                   class="flex items-center gap-2 border border-gray-100 rounded-lg px-3 py-2">
                <span class="flex-1 text-sm text-gray-600 truncate">📎 {{ file.fileName }}</span>
                <button type="button" @click="downloadFile('boards',file.id, file.fileName)"
                        class="text-xs text-blue-500 hover:text-blue-700 font-medium shrink-0 transition-colors">
                  다운로드
                </button>
                <button type="button" @click="deleteExistingFile(file.id, index)"
                        class="text-xs text-red-400 hover:text-red-600 font-medium shrink-0 transition-colors">
                  삭제
                </button>
              </div>
              <input type="file" name="file" v-for="f in fileState.fileCount"
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
            <button type="button" @click="updatePost"
                    class="px-5 py-2 text-sm text-white bg-blue-500 hover:bg-blue-600 rounded-lg transition-colors">
              수정
            </button>
          </div>
        </form>
      </div>

    </div>
  </div>
</template>
