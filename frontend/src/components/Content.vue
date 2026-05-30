<script setup>
import DOMPurify from "dompurify";

const props = defineProps({
  content: {
    type: String,
    required: true,
  },
});

const replaceContent = () => {
  return DOMPurify.sanitize(props.content.replace(/\r\n/g, '<br>'));
}
</script>

<template>
  <div class="py-4 text-gray-700 text-sm leading-relaxed min-h-[120px] mb-6" v-html="replaceContent()"></div>
</template>
