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

const listApiPath = reactive({ path: "/boards" });

const boardData = reactive({
  categories:[],
  freeBoards: [],
  page:{},
});

const search = useSearch({ categoryId: route.query.categoryId || '' });

onMounted(()=>{
  getCategories();
  getPostList();
});

const getCategories = async () => {
  try {
    const cached = useAppStore().getCategories('BOARDS');
    if (cached) {
      boardData.categories = cached;
      return;
    }
    const response = await categoryService.getCategories({ params: { boardType: 'BOARDS' } });
    useAppStore().setCategories('BOARDS', response.data.payload);
    boardData.categories = response.data.payload;
  } catch (e) {}
}

const getPostList = async () => {
  try {
    const response = await postService.getPostList(listApiPath.path, {params: search});
    setBoardDataAndPage(response.data.payload);
    await router.replace({ name:"FreeBoards", query:toQueryParams() });
  } catch (e) {}
}

const setBoardDataAndPage = (payload) => {
  boardData.freeBoards = payload.freeBoardList;
  boardData.page = payload.page;
  search.page = payload.page.currentPage;
}

const toQueryParams = () =>{
  return postUtils.toQueryParams(search);
}

const isRecentPost = (date) =>{
  return postUtils.isRecentPost(date);
}

const goToPost = (boardId) => {
  router.push({name:"FreeBoardDetail", query:route.query, params: { id: boardId }});
}

const goToCreatePost = () => {
  router.push({name:"FreeBoardCreate", query:route.query});
}
</script>

<template>
  <Header />
  <div class="min-h-screen bg-gray-50">
    <div class="container mx-auto px-4 py-8">

      <div class="mb-6 flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">자유게시판</h1>
          <p class="text-sm text-gray-500 mt-1">자유롭게 글을 작성해보세요</p>
        </div>
        <button @click="goToCreatePost"
                class="bg-blue-500 hover:bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors">
          글쓰기
        </button>
      </div>

      <div class="mb-4">
        <search-component
            @search-requested="getPostList"
            :categories="boardData.categories"
            :search="search"
            :list-api-path="listApiPath"
        />
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
          <div v-for="freeBoard in boardData.freeBoards" :key="freeBoard.id"
               @click="goToPost(freeBoard.id)"
               class="grid gap-3 items-center px-5 py-3 hover:bg-blue-50 cursor-pointer transition-colors"
               style="grid-template-columns: 3rem auto 1fr 3.5rem 9rem 5rem;">
            <span class="text-xs text-gray-400 shrink-0">{{ freeBoard.id }}</span>
            <div class="overflow-hidden">
              <span class="block text-xs text-gray-500 bg-gray-100 px-2 py-0.5 rounded-full truncate">{{ freeBoard.categoryName }}</span>
            </div>
            <div class="flex items-center gap-1 overflow-hidden">
              <span class="text-sm text-gray-700 truncate">{{ freeBoard.title }}</span>
              <span v-if="isRecentPost(freeBoard.createdDate)" class="text-xs font-bold text-red-500 shrink-0">N</span>
              <span v-if="freeBoard.commentCount > 0" class="text-xs text-blue-400 shrink-0">({{ freeBoard.commentCount }})</span>
              <span v-if="freeBoard.fileCount > 0" class="text-xs shrink-0">📎</span>
            </div>
            <span class="text-xs text-gray-400">{{ freeBoard.views }}</span>
            <span class="text-xs text-gray-400">{{ freeBoard.createdDate }}</span>
            <div class="overflow-hidden">
              <span class="block text-xs text-gray-500 truncate">{{ freeBoard.author }}</span>
            </div>
          </div>
        </div>

        <div v-if="boardData.freeBoards.length === 0"
             class="py-16 text-center text-sm text-gray-400">
          검색 결과가 없습니다
        </div>
      </div>

      <div class="mt-4">
        <pagination-component
            @page-changed="getPostList"
            :page="boardData.page"
            :search="search"
        />
      </div>

    </div>
  </div>
</template>
