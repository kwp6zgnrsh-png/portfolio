<script setup>
import PostHeader from "../../components/PostHeader.vue";
import Content from "../../components/Content.vue";
import {onMounted, reactive} from "vue";
import postService from "../../services/postService.js";
import {useRouter, useRoute} from "vue-router";
import Header from "../../components/Header.vue";

const router = useRouter();
const route = useRoute();

onMounted(()=>{
  getPostDetail();
})

const postData = reactive({
  id:0,
  categoryName: '',
  createdDate: '',
  views: 0,
  title: '',
  author:'',
});

const boardContent = reactive({
  content: '',
})

const getPostDetail = async () => {
  try {
    const boardId = route.params.id;
    const response = await postService.getPostDetail("notices", boardId);
    setDetailData(response.data.payload);
  } catch (e) {
    await router.push({name: "Notices"});
  }
}

const setDetailData = (response) =>{
  postData.id = response.id;
  postData.title = response.title;
  postData.categoryName = response.categoryName;
  postData.createdDate = response.createdDate;
  postData.views = response.views;
  postData.author = response.author;
  boardContent.content = response.content;
}

const goToNotices = () =>{
  router.push({name:"Notices", query: route.query})
}
</script>

<template>
  <Header />
  <div class="min-h-screen bg-gray-50">
    <div class="container mx-auto px-4 py-8 max-w-4xl">

      <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-4">
        <PostHeader :post-data />
        <Content :content="boardContent.content"/>
      </div>

      <div class="flex justify-center mt-6">
        <button type="button" @click="goToNotices"
                class="bg-gray-100 hover:bg-gray-200 text-gray-700 text-sm font-medium px-5 py-2 rounded-lg transition-colors">
          목록
        </button>
      </div>

    </div>
  </div>
</template>

<style scoped>

</style>