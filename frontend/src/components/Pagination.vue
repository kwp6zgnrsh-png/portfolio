<script setup>
import {computed} from 'vue';

const emit = defineEmits(["pageChanged"]);

const props = defineProps({
  page: {
    type: Object,
    required: true
  },
  search: {
    type: Object,
    required: true
  },
});

const pageNumbers = computed(() => {
  return Array.from(
      {length: props.page.endPage - props.page.startPage + 1},
      (_, i) => i + props.page.startPage);
});

const navigateToPage = (pageNumber) => {
  props.search.page = pageNumber;
  emit("pageChanged");
}
</script>

<template>
  <div class="flex justify-center items-center gap-1 mt-4">
    <button type="button" v-if="page.startPage > 1"
            @click="navigateToPage(page.prevRange)"
            class="w-8 h-8 flex items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100 transition-colors text-sm">
      ❮
    </button>

    <button type="button" v-for="pageNum in pageNumbers" :key="pageNum"
            @click="navigateToPage(pageNum)"
            class="w-8 h-8 flex items-center justify-center rounded-lg text-sm transition-colors"
            :class="pageNum === page.currentPage
              ? 'bg-blue-500 text-white font-medium'
              : 'text-gray-500 hover:bg-gray-100'">
      {{ pageNum }}
    </button>

    <button type="button" v-if="page.endPage < page.totalPages"
            @click="navigateToPage(page.nextRange)"
            class="w-8 h-8 flex items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100 transition-colors text-sm">
      ❯
    </button>
  </div>
</template>
