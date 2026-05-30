<script setup>
const props = defineProps({
  postData: {
    type: Object,
    required: true
  },
});
</script>

<template>
  <div class="mb-6">
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div class="min-w-0">
        <span class="text-xs text-gray-500 bg-gray-100 px-2 py-0.5 rounded-full">{{ postData.categoryName }}</span>
        <h1 class="text-xl font-bold text-gray-800 mt-2">{{ postData.title }}</h1>
      </div>
      <div class="text-xs text-gray-400 text-right shrink-0">
        <div class="font-medium text-gray-600">{{ postData.author }}</div>
        <div class="mt-0.5">{{ postData.createdDate }}</div>
        <div class="mt-0.5">조회 {{ postData.views }}</div>
      </div>
    </div>
    <hr class="border-t border-gray-100 mt-4"/>
  </div>
</template>