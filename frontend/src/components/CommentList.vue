<script setup>
import { reactive } from "vue";
import commentService from "../services/commentService.js";
import DOMPurify from "dompurify";

const emit = defineEmits([
  "createComment",
  "deleteComment",
]);

const props = defineProps({
  boardId: {
    type: Number,
    required: true,
  },
  comments: {
    type: Array,
    required: true
  },
  isLoggedIn: {
    type: Boolean,
    required: true
  }
});

const comment = reactive({
  boardId: 0,
  content: ''
});

const createComment = async () =>{
  try {
    comment.boardId = props.boardId;
    const response = await commentService.createComment(comment);
    comment.content = '';
    emit("createComment", response.data.payload);
  } catch (e) {
    // 에러 메세지는 axios interceptor에서 처리
  }
}

const deleteConfirm = (commentId) => {
  if(confirm("삭제하시겠습니까")){
    deleteComment(commentId);
  }
}

const deleteComment = async (commentId) => {
  try {
    await commentService.deleteComment(commentId);
    emit("deleteComment", commentId);
  } catch (e) {
    // 에러 메세지는 axios interceptor에서 처리
  }
}

const replaceComment = (comment) => {
  return DOMPurify.sanitize(comment.replace(/\n/g, '<br>'));
}

</script>

<template>
  <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-4">

    <h3 class="text-sm font-semibold text-gray-700 mb-4">
      댓글 <span class="text-blue-500">{{ comments.length }}</span>
    </h3>

    <div v-if="comments.length === 0" class="text-sm text-gray-400 text-center py-6">
      첫 번째 댓글을 남겨보세요
    </div>

    <div class="divide-y divide-gray-50">
      <div v-for="comment in comments" :key="comment.id" class="py-4">
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0">
            <div class="flex items-center gap-2 mb-1">
              <span class="text-sm font-medium text-gray-800">{{ comment.author }}</span>
              <span class="text-xs text-gray-400">{{ comment.createdDate }}</span>
            </div>
            <div class="text-sm text-gray-700 leading-relaxed" v-html="replaceComment(comment.content)"></div>
          </div>
          <button v-if="comment.isMyComment" type="button" @click="deleteConfirm(comment.id)"
                  class="text-xs text-gray-400 hover:text-red-500 shrink-0 transition-colors">
            삭제
          </button>
        </div>
      </div>
    </div>

    <div v-if="isLoggedIn" class="mt-4 pt-4 border-t border-gray-100">
      <div class="flex gap-2">
        <textarea v-model="comment.content" placeholder="댓글을 입력해 주세요"
                  class="flex-1 border border-gray-200 rounded-lg px-3 py-2.5 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-blue-100"
                  rows="2"/>
        <button type="button" @click="createComment"
                class="px-4 text-sm text-white bg-blue-500 hover:bg-blue-600 rounded-lg transition-colors self-stretch">
          등록
        </button>
      </div>
    </div>

  </div>
</template>