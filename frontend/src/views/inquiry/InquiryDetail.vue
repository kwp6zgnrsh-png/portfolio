<script setup>
import PostHeader from "../../components/PostHeader.vue";
import Content from "../../components/Content.vue";
import InquiryReply from "../../components/InquiryReply.vue";
import {onMounted, reactive} from "vue";
import postService from "../../services/postService.js";
import {useRoute, useRouter} from "vue-router";
import Header from "../../components/Header.vue";
import { useDeleteModal } from "../../composables/useDeleteModal.js";

const router = useRouter();
const route = useRoute();

onMounted(()=>{
  getPostDetail();
});

const postData = reactive({
  id:0,
  content: '',
  createdDate: '',
  views: 0,
  title: '',
  isMyPost: false,
});

const inquiryReply = reactive({
  id:0,
  content: '',
  createdDate: '',
  status: false,
});

const { isOpen: isDeleteOpen, open: openDelete, close: closeDelete } = useDeleteModal();

const getPostDetail = async () => {
  try {
    const boardId = route.params.id;
    const response = await postService.getPostDetail("inquiries", boardId);
    setDetailData(response.data.payload.inquiryDetail);
    postData.isMyPost = response.data.payload.isMyPost;
    if(response.data.payload.inquiryReply != null) updateAnswerData(response.data.payload.inquiryReply);
  } catch (e) {
    await router.push({name: "Inquiries"});
  }
};



const setDetailData = (data) =>{
  postData.id = data.id;
  postData.title = data.title;
  postData.content = data.content;
  postData.createdDate = data.createdDate;
  postData.views = data.views;
  postData.author = data.author;
};

const updateAnswerData = (data) => {
  inquiryReply.id = data.id;
  inquiryReply.content = data.content;
  inquiryReply.createdDate = data.createdDate;
  inquiryReply.author = data.author;
  inquiryReply.status = true;
};

const getListRouteName = () => {
  return history.state?.fromInquiryRoute === "MyInquiry" ? "MyInquiry" : "Inquiries";
};

const goToInquiries = () =>{
  router.push({name:getListRouteName(), query:route.query});
}

const goToUpdate = () =>{
  router.push({
    name:"InquiryUpdate",
    query:route.query,
    params: { id: postData.id },
    state: { fromInquiryRoute: getListRouteName() }
  });
}

const deletePost = async () => {
  try {
    await postService.deletePost("inquiries", postData.id);
    await router.push({name:getListRouteName(), query: route.query});
  } catch (e) {
    // 에러 메세지는 axios interceptor에서 처리
  }
}

</script>

<template>
  <Header />
  <div class="min-h-screen bg-gray-50">
    <div class="container mx-auto px-4 py-8 max-w-4xl">

      <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-4">
        <div class="mb-6">
          <div class="flex flex-wrap items-start justify-between gap-4">
            <div class="min-w-0">
              <div class="flex items-center gap-2 mb-2">
                <span class="text-xs px-2 py-0.5 rounded-full font-medium"
                      :class="inquiryReply.status ? 'bg-green-100 text-green-600' : 'bg-gray-100 text-gray-400'">
                  {{ inquiryReply.status ? '답변완료' : '미답변' }}
                </span>
              </div>
              <h1 class="text-xl font-bold text-gray-800">{{ postData.title }}</h1>
            </div>
            <div class="text-xs text-gray-400 text-right shrink-0">
              <div class="font-medium text-gray-600">{{ postData.author }}</div>
              <div class="mt-0.5">{{ postData.createdDate }}</div>
              <div class="mt-0.5">조회 {{ postData.views }}</div>
            </div>
          </div>
          <hr class="border-t border-gray-100 mt-4"/>
        </div>
        <Content :content="postData.content"/>
        <InquiryReply :reply="inquiryReply"/>
      </div>

      <div class="flex justify-center gap-3 mt-6">
        <button type="button" @click="goToInquiries"
                class="bg-gray-100 hover:bg-gray-200 text-gray-700 text-sm font-medium px-5 py-2 rounded-lg transition-colors">
          목록
        </button>
        <button v-if="postData.isMyPost && !inquiryReply.status" type="button" @click="goToUpdate"
                class="bg-blue-500 hover:bg-blue-600 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
          수정
        </button>
        <button v-if="postData.isMyPost && !inquiryReply.status" type="button" @click="openDelete"
                class="bg-red-500 hover:bg-red-600 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
          삭제
        </button>
      </div>

      <div v-if="isDeleteOpen.state" class="fixed inset-0 flex items-center justify-center bg-black/40 z-50">
        <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-sm mx-4">
          <h2 class="text-base font-semibold text-gray-800">게시글을 삭제하시겠습니까?</h2>
          <p class="text-sm text-gray-500 mt-1">삭제된 게시글은 복구할 수 없습니다.</p>
          <div class="flex justify-end gap-2 mt-6">
            <button @click="closeDelete"
                    class="px-4 py-2 text-sm text-gray-600 bg-gray-100 hover:bg-gray-200 rounded-lg transition-colors">
              취소
            </button>
            <button @click="deletePost"
                    class="px-4 py-2 text-sm text-white bg-red-500 hover:bg-red-600 rounded-lg transition-colors">
              삭제
            </button>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<style scoped>
</style>
