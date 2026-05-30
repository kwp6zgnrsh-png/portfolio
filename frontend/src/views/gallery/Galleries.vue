<script setup>
import searchComponent from '../../components/Search.vue'
import paginationComponent from '../../components/Pagination.vue'
import {onMounted, reactive} from "vue";
import {useRoute, useRouter} from "vue-router";
import postService from "../../services/postService.js";
import categoryService from "../../services/categoryService.js";
import fileService from "../../services/fileService.js";
import postUtils from "../../services/postUtils.js";
import Header from "../../components/Header.vue";
import { useSearch } from "../../composables/useSearch.js";
import {useAppStore} from "../../store/index.js";

const route = useRoute();
const router = useRouter();

const listApiPath = reactive({path:"/galleries"});

const search = useSearch({ categoryId: route.query.categoryId || '' });

const boardData = reactive({
  galleries:[],
  categories:[],
  page:{},
});

onMounted(()=>{
  getCategories();
  getPostList();
});

const getCategories = async () => {
  const cached = useAppStore().getCategories('GALLERIES');
  if (cached) {
    boardData.categories = cached;
    return;
  }
  const response = await categoryService.getCategories({ params: { boardType: 'GALLERIES' } });
  useAppStore().setCategories('GALLERIES', response.data.payload);
  boardData.categories = response.data.payload;
}

const getPostList = async () => {
  const response = await postService.getPostList(listApiPath.path, {params:search});
  setListData(response.data.payload);
  await router.replace({name:"Galleries", query:toQueryParams()});
};

const setListData = (response) => {
  boardData.galleries = response.galleryList;
  boardData.page = response.page;
};

const getImageUrl = (path, name, extension) => {
  return fileService.getImageUrl(path, name, extension);
};

const toQueryParams = () =>{
  return postUtils.toQueryParams(search);
};

const goToPost = (boardId) => {
  router.push({name:"GalleryDetail", query:route.query, params: { id: boardId }});
};

const isRecentPost = (date) =>{
  return postUtils.isRecentPost(date);
};

const goToCreatePost = () => {
  router.push({name:"GalleryCreate", query:route.query});
}

</script>

<template>
  <Header />
  <div class="min-h-screen bg-gray-50">
    <div class="container mx-auto px-4 py-8">

      <div class="mb-6 flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">갤러리</h1>
          <p class="text-sm text-gray-500 mt-1">사진과 이미지를 공유해보세요</p>
        </div>
        <button @click="goToCreatePost"
                class="bg-blue-500 hover:bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors">
          글쓰기
        </button>
      </div>

      <div class="mb-4">
        <search-component @search-requested="getPostList" :categories="boardData.categories" :list-api-path="listApiPath" :search />
      </div>

      <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
        <div v-for="gallery in boardData.galleries" :key="gallery.id"
             @click="goToPost(gallery.id)"
             class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden cursor-pointer hover:shadow-md hover:-translate-y-0.5 transition-all group">
          <div class="relative">
            <img class="w-full h-44 object-cover"
                 :src="getImageUrl(gallery.path, gallery.storeName, gallery.extension)"
                 alt="">
            <div v-if="gallery.fileCount > 1"
                 class="absolute top-2 right-2 bg-black/50 text-white text-xs px-1.5 py-0.5 rounded-md">
              +{{ gallery.fileCount - 1 }}
            </div>
            <span v-if="isRecentPost(gallery.createdDate)"
                  class="absolute top-2 left-2 text-xs font-bold text-white bg-red-500 px-1.5 py-0.5 rounded-md">N</span>
          </div>
          <div class="p-3">
            <span class="text-xs text-gray-600 bg-gray-100 px-2 py-0.5 rounded-full">{{ gallery.categoryName }}</span>
            <p class="text-sm font-medium text-gray-800 mt-2 truncate pl-1">{{ gallery.title }}</p>
            <p class="text-xs text-gray-400 mt-1 truncate pl-1">{{ gallery.content }}</p>
            <div class="flex items-center justify-between mt-2">
              <span class="text-xs text-gray-400 pl-1">{{ gallery.author }}</span>
              <span class="text-xs text-gray-400 pr-1">{{ gallery.createdDate }}</span>
            </div>
          </div>
        </div>
      </div>

      <div v-if="boardData.galleries.length === 0"
           class="bg-white rounded-xl shadow-sm border border-gray-100 py-16 text-center text-sm text-gray-400 mt-4">
        검색 결과가 없습니다
      </div>

      <div class="mt-4">
        <pagination-component @page-changed="getPostList" :search :page="boardData.page" />
      </div>

    </div>
  </div>
</template>
<style scoped>
</style>
