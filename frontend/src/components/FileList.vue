<script setup>
import fileService from "../services/fileService.js";

const props = defineProps({
  boardType: String,
  files: {
    type: Array,
    required: true
  },
});

const downloadFile = async (boardType,fileId, fileName) => {
  await fileService.downloadFile(boardType,fileId, fileName);
}
</script>

<template>
  <div v-for="file in files" :key="file.id" class="flex items-center py-1">
    <span class="text-blue-500 mr-2">📁</span>
    <button
        type="button"
        @click="downloadFile(boardType,file.id, file.fileName)"
        class="text-blue-600 hover:underline"
    >
      {{ file.fileName }}
    </button>
  </div>
  <div class="mb-2"></div>
</template>