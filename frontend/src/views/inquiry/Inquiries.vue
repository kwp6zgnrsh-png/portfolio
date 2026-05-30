<script setup>
import searchComponent from '../../components/Search.vue'
import paginationComponent from '../../components/Pagination.vue'
import {onMounted, reactive} from "vue";
import {useRoute, useRouter} from "vue-router";
import postService from "../../services/postService.js";
import secretPostService from "../../services/secretPostService.js";
import postUtils from "../../services/postUtils.js";
import {useAppStore} from "../../store/index.js";
import Header from "../../components/Header.vue";
import { useSearch } from "../../composables/useSearch.js";

const router = useRouter();
const route = useRoute();

const listApiPath = reactive({path:"/inquiries"});

const boardData = reactive({
  inquiryBoards: [],
  page:{},
  isLoggedIn: false,
})

const search = useSearch({ onlyMine: false });

onMounted(()=>{
  search.onlyMine = route.name === "MyInquiry";
  boardData.isLoggedIn = useAppStore().isLoggedIn;
  getPostList();
});

const getListRouteName = () => search.onlyMine ? "MyInquiry" : "Inquiries";

const getPostList = async () => {
  try {
    const response = await postService.getPostList(listApiPath.path, {params: search});
    setListData(response.data.payload);
    await router.replace({name:getListRouteName(), query: toQueryParams() });
  } catch (e) {}
};

const toQueryParams = () =>{
  return postUtils.toQueryParams(search);
};

const setListData = (response) => {
  boardData.inquiryBoards = response.inquiryList;
  boardData.page = response.page;
};

const goToPost = async (boardId, isSecret) => {
  const state = { fromInquiryRoute: getListRouteName() };
  if(isSecret){
    const response = await secretPostService.verifyMySecretPost("inquiries", boardId);
    if(response.data.message === "본인"){
      await router.push({name:"InquiryDetail", params: { id: boardId }, query: route.query, state});
    }else if(response.data.message === "비밀번호입력"){
      await router.push({name:"InquiryValidate", params: { id: boardId }, query: route.query, state});
    }
  }else {
    await router.push({name:"InquiryDetail", params: { id: boardId }, query: route.query, state});
  }
};

const isRecentPost = (date) =>{
  return postUtils.isRecentPost(date);
};

const goToCreatePost = () => {
  router.push({name:"InquiryCreate", query:route.query});
}
</script>

<template>
  <Header />
  <div class="min-h-screen bg-gray-50">
    <div class="container mx-auto px-4 py-8">

      <div class="mb-6 flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">문의게시판</h1>
          <p class="text-sm text-gray-500 mt-1">궁금한 점을 문의해보세요</p>
        </div>
        <button @click="goToCreatePost"
                class="bg-blue-500 hover:bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors">
          글쓰기
        </button>
      </div>

      <div class="mb-4">
        <search-component @search-requested="getPostList" :list-api-path="listApiPath" :search />
      </div>

      <div v-if="boardData.isLoggedIn"
           class="flex items-center gap-2 mb-4 px-1">
        <input type="checkbox" id="onlyMine" v-model="search.onlyMine" @change="getPostList"
               class="w-4 h-4 text-blue-500 rounded border-gray-300 cursor-pointer">
        <label for="onlyMine" class="text-sm text-gray-600 cursor-pointer">나의 문의내역만 보기</label>
      </div>

      <div class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
        <div class="grid gap-3 px-5 py-3 border-b border-gray-100 bg-gray-50 text-xs font-medium text-gray-400"
             style="grid-template-columns: 3rem 1fr 5rem 9rem 5rem;">
          <span>번호</span>
          <span>제목</span>
          <span>조회</span>
          <span>등록일</span>
          <span>작성자</span>
        </div>

        <div class="divide-y divide-gray-50">
          <div v-for="inquiry in boardData.inquiryBoards" :key="inquiry.id"
               @click="goToPost(inquiry.id, inquiry.isSecret)"
               class="grid gap-3 items-center px-5 py-3 hover:bg-blue-50 cursor-pointer transition-colors"
               style="grid-template-columns: 3rem 1fr 5rem 9rem 5rem;">
            <span class="text-xs text-gray-400 shrink-0">{{ inquiry.id }}</span>
            <div class="flex items-center gap-2 overflow-hidden">
              <div class="flex-1 overflow-hidden">
                <span class="block text-sm text-gray-700 truncate">{{ inquiry.title }}</span>
              </div>
              <span v-if="inquiry.isSecret" class="text-xs shrink-0">🔒</span>
              <span v-if="isRecentPost(inquiry.createdDate)" class="text-xs font-bold text-red-500 shrink-0">N</span>
              <span class="text-xs px-1.5 py-0.5 rounded-full shrink-0"
                    :class="inquiry.answerCount > 0 ? 'bg-green-100 text-green-600' : 'bg-gray-100 text-gray-400'">
                {{ inquiry.answerCount > 0 ? '답변완료' : '미답변' }}
              </span>
            </div>
            <span class="text-xs text-gray-400">{{ inquiry.views }}</span>
            <span class="text-xs text-gray-400">{{ inquiry.createdDate }}</span>
            <div class="overflow-hidden">
              <span class="block text-xs text-gray-500 truncate">{{ inquiry.author }}</span>
            </div>
          </div>
        </div>

        <div v-if="boardData.inquiryBoards.length === 0"
             class="py-16 text-center text-sm text-gray-400">
          검색 결과가 없습니다
        </div>
      </div>

      <div class="mt-4">
        <pagination-component @page-changed="getPostList" :search :page="boardData.page"/>
      </div>

    </div>
  </div>
</template>
