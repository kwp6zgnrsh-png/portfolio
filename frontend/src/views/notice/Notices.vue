<script setup>
import searchComponent from '../../components/Search.vue'
import paginationComponent from '../../components/Pagination.vue'
import {onMounted, reactive} from "vue";
import {useRoute, useRouter} from "vue-router";
import postService from "../../services/postService.js";
import categoryService from "../../services/categoryService.js";
import postUtils from "../../services/postUtils.js";
import Header from "../../components/Header.vue";
import { useSearch } from "../../composables/useSearch.js";
import {useAppStore} from "../../store/index.js";

const router = useRouter();
const route = useRoute();

onMounted(()=>{
  getCategories();
  getPostList();
})

const listApiPath = reactive({path:"/notices"});

const boardData = reactive({
  categories: [],
  noticeList: [],
  pinnedNoticeList: [],
  page: {},
});

const search = useSearch({ categoryId: route.query.categoryId || '' });

const getCategories = async () => {
  const cached = useAppStore().getCategories('NOTICES');
  if (cached) {
    boardData.categories = cached;
    return;
  }
  const response = await categoryService.getCategories({ params: { boardType: 'NOTICES' } });
  useAppStore().setCategories('NOTICES', response.data.payload);
  boardData.categories = response.data.payload;
}


const getPostList = async () => {
  const response = await postService.getPostList(listApiPath.path, {params:search});
  setListData(response.data.payload);
  await router.replace({name:"Notices", query:toQueryParams()});
}

const setListData = (response) => {
  boardData.noticeList = response.noticeList;
  boardData.pinnedNoticeList = response.pinnedNoticeList;
  boardData.page = response.page;
}

const goToPost = (boardId) => {
  router.push({name:"NoticeDetail", query:route.query, params: { id: boardId }});
}

const toQueryParams = () => {
  return postUtils.toQueryParams(search);
}

const isRecentPost = (date) =>{
  return postUtils.isRecentPost(date);
}
</script>

<template>
  <Header />
  <div class="min-h-screen bg-gray-50">
    <div class="container mx-auto px-4 py-8">

      <div class="mb-6">
        <h1 class="text-2xl font-bold text-gray-800">공지사항</h1>
        <p class="text-sm text-gray-500 mt-1">중요한 공지를 확인하세요</p>
      </div>

      <div class="mb-4">
        <search-component @search-requested="getPostList" :categories="boardData.categories" :search :list-api-path="listApiPath"/>
      </div>

      <div class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
        <div class="grid gap-3 px-5 py-3 border-b border-gray-100 bg-gray-50 text-xs font-medium text-gray-400"
             style="grid-template-columns: 3rem auto 1fr 3.5rem 9rem 5rem;">
          <span>번호</span>
          <span>분류</span>
          <span>제목</span>
          <span>조회</span>
          <span>등록일</span>
          <span>작성자</span>
        </div>

        <div class="divide-y divide-gray-50">
          <!-- 고정 공지 -->
          <div v-for="notice in boardData.pinnedNoticeList" :key="'p-' + notice.id"
               @click="goToPost(notice.id)"
               class="grid gap-3 items-center px-5 py-3 bg-amber-50 hover:bg-amber-100 cursor-pointer transition-colors"
               style="grid-template-columns: 3rem auto 1fr 3.5rem 9rem 5rem;">
            <span class="text-sm">📌</span>
            <div class="overflow-hidden">
              <span class="block text-xs text-amber-600 bg-amber-100 px-2 py-0.5 rounded-full truncate">{{ notice.categoryName }}</span>
            </div>
            <div class="flex items-center gap-1 overflow-hidden">
              <span class="text-sm text-gray-800 font-medium truncate">{{ notice.title }}</span>
              <span v-if="isRecentPost(notice.createdDate)" class="text-xs font-bold text-red-500 shrink-0">N</span>
            </div>
            <span class="text-xs text-gray-400">{{ notice.views }}</span>
            <span class="text-xs text-gray-400">{{ notice.createdDate }}</span>
            <div class="overflow-hidden">
              <span class="block text-xs text-gray-500 truncate">{{ notice.author }}</span>
            </div>
          </div>

          <!-- 일반 공지 -->
          <div v-for="notice in boardData.noticeList" :key="notice.id"
               @click="goToPost(notice.id)"
               class="grid gap-3 items-center px-5 py-3 hover:bg-blue-50 cursor-pointer transition-colors"
               style="grid-template-columns: 3rem auto 1fr 3.5rem 9rem 5rem;">
            <span class="text-xs text-gray-400 shrink-0">{{ notice.id }}</span>
            <div class="overflow-hidden">
              <span class="block text-xs text-gray-500 bg-gray-100 px-2 py-0.5 rounded-full truncate">{{ notice.categoryName }}</span>
            </div>
            <div class="flex items-center gap-1 overflow-hidden">
              <span class="text-sm text-gray-700 truncate">{{ notice.title }}</span>
              <span v-if="isRecentPost(notice.createdDate)" class="text-xs font-bold text-red-500 shrink-0">N</span>
            </div>
            <span class="text-xs text-gray-400">{{ notice.views }}</span>
            <span class="text-xs text-gray-400">{{ notice.createdDate }}</span>
            <div class="overflow-hidden">
              <span class="block text-xs text-gray-500 truncate">{{ notice.author }}</span>
            </div>
          </div>
        </div>

        <div v-if="boardData.noticeList.length === 0 && boardData.pinnedNoticeList.length === 0"
             class="py-16 text-center text-sm text-gray-400">
          검색 결과가 없습니다
        </div>
      </div>

      <div class="mt-4">
        <pagination-component @page-changed="getPostList" :page="boardData.page" :search />
      </div>

    </div>
  </div>
</template>
