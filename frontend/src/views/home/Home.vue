<script setup>

import {onMounted, reactive} from "vue";
import homeService from "../../services/homeService.js";
import secretPostService from "../../services/secretPostService.js";
import fileService from "../../services/fileService.js";
import postUtils from "../../services/postUtils.js";
import {useRouter} from "vue-router";
import Header from "../../components/Header.vue";

const router = useRouter();

const board = reactive({
  noticeList: [],
  freeBoardList: [],
  galleryList: [],
  inquiryList: [],
})

onMounted(()=>{
  getPostList();
})

const getPostList = async () => {
  try {
    const response = await homeService.getHome();
    board.noticeList = response.data.payload.noticeList;
    board.freeBoardList = response.data.payload.freeBoardList;
    board.galleryList = response.data.payload.galleryList;
    board.inquiryList = response.data.payload.inquiryList;
  } catch (e) {}
};

const isRecentPost = (date) =>{
  return postUtils.isRecentPost(date);
};

const getImageUrl = (path, name, extension) => {
  return fileService.getImageUrl(path, name, extension);
};

const goToBoards = (board) => {
  router.push({ name:board });
}

const goToPost = (name, id) =>{
  router.push({name: name, params: { id }});
}

const goToMyInquiry = () => {
  router.push({name:"MyInquiry"});
}

const goToInquiry = async (boardId, checked) => {
  if(checked){
    const response = await secretPostService.verifyMySecretPost("inquiries", boardId);
    if(response.data.message === "본인"){
      await router.push({name:"InquiryDetail", params: { id: boardId }});
    }else {
      await router.push({name:"InquiryValidate", params: { id: boardId }});
    }
  }else {
    await router.push({name:"InquiryDetail", params: { id: boardId }});
  }
};

</script>

<template>
  <Header />
  <div class="min-h-screen bg-gray-50">
    <div class="container mx-auto px-4 py-8">
      <div class="mb-8">
        <h1 class="text-2xl font-bold text-gray-800">커뮤니티</h1>
        <p class="text-sm text-gray-500 mt-1">최신 게시글을 확인하세요</p>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-2 gap-6">

        <!-- 공지사항 -->
        <div class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
          <div class="flex items-center justify-between px-5 py-4 border-b border-gray-100">
            <div class="flex items-center gap-2">
              <span class="text-lg">📢</span>
              <h2 class="font-semibold text-gray-700">공지사항</h2>
            </div>
            <button @click="goToBoards('Notices')"
                    class="text-xs text-blue-500 hover:text-blue-700 font-medium transition-colors">
              더보기 →
            </button>
          </div>
          <div class="divide-y divide-gray-50">
            <div v-for="notice in board.noticeList" :key="notice.id"
                 @click="goToPost('NoticeDetail', notice.id)"
                 class="flex items-center gap-3 px-5 py-3 hover:bg-blue-50 cursor-pointer transition-colors">
              <span class="text-xs text-gray-400 shrink-0">{{ notice.id }}</span>
              <span class="text-xs text-gray-500 bg-gray-100 px-2 py-0.5 rounded-full shrink-0">{{ notice.categoryName }}</span>
              <div class="flex-1 overflow-hidden">
                <span class="block text-sm text-gray-700 truncate">{{ notice.title }}</span>
              </div>
              <span v-if="isRecentPost(notice.createdDate)"
                    class="text-xs font-bold text-red-500 shrink-0">N</span>
            </div>
          </div>
        </div>

        <!-- 자유게시판 -->
        <div class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
          <div class="flex items-center justify-between px-5 py-4 border-b border-gray-100">
            <div class="flex items-center gap-2">
              <span class="text-lg">📄</span>
              <h2 class="font-semibold text-gray-700">자유게시판</h2>
            </div>
            <button @click="goToBoards('FreeBoards')"
                    class="text-xs text-blue-500 hover:text-blue-700 font-medium transition-colors">
              더보기 →
            </button>
          </div>
          <div class="divide-y divide-gray-50">
            <div v-for="freeBoard in board.freeBoardList" :key="freeBoard.id"
                 @click="goToPost('FreeBoardDetail', freeBoard.id)"
                 class="flex items-center gap-3 px-5 py-3 hover:bg-blue-50 cursor-pointer transition-colors">
              <span class="text-xs text-gray-400 shrink-0">{{ freeBoard.id }}</span>
              <span class="text-xs text-gray-500 bg-gray-100 px-2 py-0.5 rounded-full shrink-0">{{ freeBoard.categoryName }}</span>
              <div class="flex-1 overflow-hidden">
                <span class="block text-sm text-gray-700 truncate">{{ freeBoard.title }}</span>
              </div>
              <div class="flex items-center gap-1 shrink-0">
                <span v-if="isRecentPost(freeBoard.createdDate)" class="text-xs font-bold text-red-500">N</span>
                <span v-if="freeBoard.commentCount > 0" class="text-xs text-blue-400">({{ freeBoard.commentCount }})</span>
                <span v-if="freeBoard.fileCount > 0" class="text-xs">📎</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 갤러리 -->
        <div class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
          <div class="flex items-center justify-between px-5 py-4 border-b border-gray-100">
            <div class="flex items-center gap-2">
              <span class="text-lg">📷</span>
              <h2 class="font-semibold text-gray-700">갤러리</h2>
            </div>
            <button @click="goToBoards('Galleries')"
                    class="text-xs text-blue-500 hover:text-blue-700 font-medium transition-colors">
              더보기 →
            </button>
          </div>
          <div class="divide-y divide-gray-50">
            <div v-for="gallery in board.galleryList" :key="gallery.id"
                 @click="goToPost('GalleryDetail', gallery.id)"
                 class="flex items-center gap-3 px-5 py-3 hover:bg-blue-50 cursor-pointer transition-colors">
              <img :src="getImageUrl(gallery.path, gallery.storeName, gallery.extension)"
                   class="w-16 h-10 object-cover rounded-md shrink-0 bg-gray-100">
              <div class="flex flex-col min-w-0">
                <span class="text-sm text-gray-700 truncate">{{ gallery.title }}</span>
                <span class="text-xs text-gray-400">{{ gallery.categoryName }}</span>
              </div>
              <div class="flex items-center gap-1 shrink-0 ml-auto">
                <span v-if="gallery.fileCount > 1" class="text-xs text-gray-400">+{{ gallery.fileCount - 1 }}</span>
                <span v-if="isRecentPost(gallery.createdDate)" class="text-xs font-bold text-red-500">N</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 문의게시판 -->
        <div class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
          <div class="flex items-center justify-between px-5 py-4 border-b border-gray-100">
            <button @click="goToMyInquiry"
                    class="text-xs text-gray-400 hover:text-gray-600 font-medium transition-colors">
              나의 문의 →
            </button>
            <div class="flex items-center gap-2">
              <span class="text-lg">✉️</span>
              <h2 class="font-semibold text-gray-700">문의게시판</h2>
            </div>
            <button @click="goToBoards('Inquiries')"
                    class="text-xs text-blue-500 hover:text-blue-700 font-medium transition-colors">
              더보기 →
            </button>
          </div>
          <div class="divide-y divide-gray-50">
            <div v-for="inquiry in board.inquiryList" :key="inquiry.id"
                 @click="goToInquiry(inquiry.id, inquiry.isSecret)"
                 class="flex items-center gap-3 px-5 py-3 hover:bg-blue-50 cursor-pointer transition-colors">
              <span class="text-xs text-gray-400 shrink-0">{{ inquiry.id }}</span>
              <div class="flex-1 overflow-hidden">
                <span class="block text-sm text-gray-700 truncate">{{ inquiry.title }}</span>
              </div>
              <div class="flex items-center gap-1 shrink-0 ml-auto">
                <span v-if="inquiry.isSecret" class="text-xs">🔒</span>
                <span v-if="isRecentPost(inquiry.createdDate)" class="text-xs font-bold text-red-500">N</span>
                <span class="text-xs px-1.5 py-0.5 rounded-full"
                      :class="inquiry.answer > 0 ? 'bg-green-100 text-green-600' : 'bg-gray-100 text-gray-400'">
                  {{ inquiry.answer > 0 ? '답변완료' : '미답변' }}
                </span>
              </div>
            </div>
          </div>
        </div>

      </div>
    </div>
  </div>
</template>
