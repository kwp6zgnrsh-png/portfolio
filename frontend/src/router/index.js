import { createRouter, createWebHistory } from 'vue-router'
import { useAppStore } from "../store/index.js";

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/home/Home.vue'),
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/auth/Login.vue')
  },
  {
    path: '/sign-up',
    name: 'SignUp',
    component: () => import('../views/auth/SignUp.vue')
  },
  {
    path: '/notices',
    name: 'Notices',
    component: () => import('../views/notice/Notices.vue')
  },
  {
    path: '/notice/:id',
    name: 'NoticeDetail',
    component: () => import('../views/notice/NoticeDetail.vue')
  },
  {
    path: '/boards',
    name: 'FreeBoards',
    component: () => import('../views/free-board/FreeBoards.vue')
  },
  {
    path: '/board/create',
    name: 'FreeBoardCreate',
    meta: { requiresAuth: true },
    component: () => import('../views/free-board/FreeBoardCreate.vue')
  },
  {
    path: '/board/update/:id',
    name: 'FreeBoardUpdate',
    meta: { requiresAuth: true },
    component: () => import('../views/free-board/FreeBoardUpdate.vue'),
  },
  {
    path: '/board/:id',
    name: 'FreeBoardDetail',
    component: () => import('../views/free-board/FreeBoardDetail.vue')
  },
  {
    path: '/galleries',
    name: 'Galleries',
    component: () => import('../views/gallery/Galleries.vue')
  },
  {
    path: '/gallery/create',
    name: 'GalleryCreate',
    meta: { requiresAuth: true },
    component: () => import('../views/gallery/GalleryCreate.vue')
  },
  {
    path: '/gallery/update/:id',
    name: 'GalleryUpdate',
    meta: { requiresAuth: true },
    component: () => import('../views/gallery/GalleryUpdate.vue')
  },
  {
    path: '/gallery/:id',
    name: 'GalleryDetail',
    component: () => import('../views/gallery/GalleryDetail.vue')
  },
  {
    path: '/inquiries',
    name: 'Inquiries',
    component: () => import('../views/inquiry/Inquiries.vue')
  },
  {
    path: '/inquiries/myInquiry',
    name: 'MyInquiry',
    meta: { requiresAuth: true },
    component: () => import('../views/inquiry/Inquiries.vue')
  },
  {
    path: '/inquiry/create',
    name: 'InquiryCreate',
    meta: { requiresAuth: true },
    component: () => import('../views/inquiry/InquiryCreate.vue')
  },
  {
    path: '/inquiry/update/:id',
    name: 'InquiryUpdate',
    meta: { requiresAuth: true },
    component: () => import('../views/inquiry/InquiryUpdate.vue')
  },
  {
    path: '/inquiry/validate/:id',
    name: 'InquiryValidate',
    component: () => import('../views/inquiry/InquiryVerify.vue')
  },
  {
    path: '/inquiry/:id',
    name: 'InquiryDetail',
    component: () => import('../views/inquiry/InquiryDetail.vue')
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Navigation Guard
router.beforeEach((to, from, next) => {
  const isAuthenticated = useAppStore().isLoggedIn;

  if (to.matched.some(record => record.meta.requiresAuth) && !isAuthenticated) {
    useAppStore().setRedirectPath(to.fullPath);
    next('/login');
  } else {
    next();
  }
});

export default router
