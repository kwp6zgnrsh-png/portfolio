<script setup>
const props = defineProps({
  reply: {
    type: Object,
    required: true
  }
});
</script>

<template>
  <div class="rounded-xl border mb-4 overflow-hidden"
       :class="reply.status ? 'border-blue-100 bg-blue-50' : 'border-gray-100 bg-gray-50'">
    <div class="flex items-center gap-2 px-4 py-2.5 border-b"
         :class="reply.status ? 'border-blue-100 bg-blue-100/50' : 'border-gray-100'">
      <span class="text-xs font-semibold"
            :class="reply.status ? 'text-blue-600' : 'text-gray-400'">
        {{ reply.status ? '답변완료' : '미답변' }}
      </span>
    </div>
    <div class="px-4 py-4">
      <div v-if="reply.status">
        <div class="flex justify-between items-center mb-2">
          <span class="text-sm font-medium text-gray-700">{{ reply.author }}</span>
          <span class="text-xs text-gray-400">{{ reply.createdDate }}</span>
        </div>
        <p class="text-sm text-gray-700 leading-relaxed">{{ reply.content }}</p>
      </div>
      <p v-else class="text-sm text-gray-400">아직 등록된 답변이 없습니다.</p>
    </div>
  </div>
</template>

<style scoped>

</style>