<script setup>

import {reactive} from "vue";
import {useRouter} from "vue-router";
import {useAppStore} from "../store/index.js";
import authService from "../services/authService.js";

const router = useRouter();
const store = useAppStore();

const navLinks = reactive([
  { name: 'home', route: 'Home', text: '🏠 홈' },
  { name: 'notices', route: 'Notices', text: '📢 공지사항' },
  { name: 'freeboards', route: 'FreeBoards', text: '📄 자유게시판' },
  { name: 'galleries', route: 'Galleries', text: '📷 갤러리' },
  { name: 'inquiries', route: 'Inquiries', text: '✉️ 문의게시판' },
]);

const logout = async () => {
  try {
    await authService.logout();
  } catch (e) {

  }
  store.setMemberName('');
  store.setMemberId('');
  window.location.href = '/';
}
</script>

<template>
  <header class="bg-white border-b border-gray-100 shadow-sm sticky top-0 z-50">
    <div class="container mx-auto px-4">
      <div class="flex items-center justify-between h-14">

        <nav class="flex items-center gap-1">
          <router-link
              v-for="link in navLinks"
              :key="link.name"
              :to="{ name: link.route }"
              class="px-3 py-1.5 rounded-lg text-sm text-gray-600 hover:text-blue-600 hover:bg-blue-50 transition-colors"
              active-class="text-blue-600 bg-blue-50 font-medium"
          >
            {{ link.text }}
          </router-link>
        </nav>

        <div v-if="!store.isLoggedIn" class="flex items-center gap-1">
          <router-link :to="{ name: 'Login' }"
                       class="px-3 py-1.5 rounded-lg text-sm text-gray-600 hover:text-blue-600 hover:bg-blue-50 transition-colors">
            로그인
          </router-link>
          <span class="text-gray-300">|</span>
          <router-link :to="{ name: 'SignUp' }"
                       class="px-3 py-1.5 rounded-lg text-sm text-gray-600 hover:text-blue-600 hover:bg-blue-50 transition-colors">
            회원가입
          </router-link>
        </div>

        <div v-if="store.isLoggedIn" class="flex items-center gap-2">
          <span class="text-sm text-gray-600 font-medium">{{ store.getMemberName }}</span>
          <button @click="logout"
                  class="px-3 py-1.5 rounded-lg text-sm text-gray-400 hover:text-red-500 hover:bg-red-50 transition-colors">
            로그아웃
          </button>
        </div>

      </div>
    </div>
  </header>
</template>

<style scoped>
</style>