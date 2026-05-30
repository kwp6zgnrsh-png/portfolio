<script setup>
import PostHeader from "../../components/PostHeader.vue";
import Content from "../../components/Content.vue";
import {onMounted, reactive} from "vue";
import CommentList from "../../components/CommentList.vue";
import FileList from "../../components/FileList.vue";
import postService from "../../services/postService.js";
import {useRoute, useRouter} from "vue-router";
import Header from "../../components/Header.vue";
import {useDeleteModal} from "../../composables/useDeleteModal.js";
import {useAppStore} from "../../store/index.js";

const route = useRoute();
const router = useRouter();

const postData = reactive({
  id:0,
  categoryName: '',
  createdDate: '',
  views: 0,
  title: '',
  author:'',
  content: '',
  isLoggedIn: false,
  isMyPost: false,
  files: [],
  comments: [],
});

const { isOpen: isDeleteOpen, open: openDelete, close: closeDelete } = useDeleteModal();

onMounted(()=>{
  postData.isLoggedIn = useAppStore().isLoggedIn;
  getPostDetail();
});

const getPostDetail = async () => {
  try {
    const boardId = route.params.id;
    const response = await postService.getPostDetail("boards", boardId);
    setPostData(response.data.payload);
  } catch (e) {
    await router.push({name: "FreeBoards"});
  }
}

const setPostData = (response) =>{
  postData.id = response.freeBoardDetail.id;
  postData.title = response.freeBoardDetail.title;
  postData.categoryName = response.freeBoardDetail.categoryName;
  postData.createdDate = response.freeBoardDetail.createdDate;
  postData.views = response.freeBoardDetail.views;
  postData.author = response.freeBoardDetail.author;
  postData.content = response.freeBoardDetail.content;
  postData.comments = response.commentList;
  postData.files = response.fileList;
  postData.isMyPost = response.isMyPost;
}

const createComment = (response) => {
  postData.comments.push(response);
}

const deleteComment = (response) => {
  postData.comments = postData.comments.filter(comment => comment.id !== response);
}

const goToFreeBoards = () =>{
  router.push({name:"FreeBoards", query:route.query});
}

const goToUpdate = () =>{
  router.push({name:"FreeBoardUpdate", query:route.query, params:{id: postData.id}});
}

const deletePost = async () => {
  try {
    await postService.deletePost("boards", postData.id);
    await router.push({name:"FreeBoards"});
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
        <PostHeader :post-data />
        <Content :content="postData.content"/>
        <FileList :boardType="'boards'" :files="postData.files"/>
      </div>

      <CommentList @create-comment="createComment"
                   @delete-comment="deleteComment"
                   :boardId="postData.id"
                   :comments="postData.comments"
                   :isLoggedIn="postData.isLoggedIn"/>

      <div class="flex justify-center gap-3 mt-6">
        <button type="button" @click="goToFreeBoards"
                class="bg-gray-100 hover:bg-gray-200 text-gray-700 text-sm font-medium px-5 py-2 rounded-lg transition-colors">
          목록
        </button>
        <button v-if="postData.isMyPost" type="button" @click="goToUpdate"
                class="bg-blue-500 hover:bg-blue-600 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
          수정
        </button>
        <button v-if="postData.isMyPost" type="button" @click="openDelete"
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