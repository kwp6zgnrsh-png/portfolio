<script setup>
import { ref } from 'vue';

const props = defineProps({
  images: { type: Array, required: true }
});

const current = ref(0);
const prev = () => current.value = (current.value - 1 + props.images.length) % props.images.length;
const next = () => current.value = (current.value + 1) % props.images.length;
const goTo = (i) => current.value = i;
</script>

<template>
  <div class="rounded-xl overflow-hidden bg-gray-900 shadow-lg select-none">

    <div class="relative">
      <img :src="images[current]"
           class="w-full h-[480px] object-contain bg-gray-900 transition-opacity duration-200"
           alt="">

      <button v-if="images.length > 1" @click="prev"
              class="absolute left-3 top-1/2 -translate-y-1/2 w-10 h-10 bg-black/40 hover:bg-black/70 text-white text-xl rounded-full flex items-center justify-center transition-colors">
        ‹
      </button>
      <button v-if="images.length > 1" @click="next"
              class="absolute right-3 top-1/2 -translate-y-1/2 w-10 h-10 bg-black/40 hover:bg-black/70 text-white text-xl rounded-full flex items-center justify-center transition-colors">
        ›
      </button>

      <div v-if="images.length > 1"
           class="absolute bottom-3 right-3 bg-black/50 text-white text-xs px-2.5 py-1 rounded-full">
        {{ current + 1 }} / {{ images.length }}
      </div>
    </div>

    <div v-if="images.length > 1" class="flex gap-2 p-3 bg-gray-800 overflow-x-auto">
      <img v-for="(img, i) in images" :key="i" :src="img"
           @click="goTo(i)"
           class="w-16 h-12 object-cover rounded-md cursor-pointer flex-shrink-0 transition-all duration-200"
           :class="i === current ? 'ring-2 ring-white opacity-100 scale-105' : 'opacity-40 hover:opacity-70'">
    </div>

  </div>
</template>
