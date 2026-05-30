import { defineStore } from 'pinia';

const SESSION_KEY = 'memberName';

export const useAppStore = defineStore('store', {
  state: () => ({
    memberName: sessionStorage.getItem(SESSION_KEY) ?? '',
    memberId: sessionStorage.getItem('memberId') ?? '',
    redirectPath: '',
    categoryCache: {},
  }),
  actions: {
    setMemberName(memberName) {
      this.memberName = memberName;
      if (memberName) {
        sessionStorage.setItem(SESSION_KEY, memberName);
      } else {
        sessionStorage.removeItem(SESSION_KEY);
      }
    },
    setMemberId(memberId) {
      this.memberId = memberId;
      if (memberId) {
        sessionStorage.setItem('memberId', memberId);
      } else {
        sessionStorage.removeItem('memberId');
      }
    },
    setRedirectPath(path) {
      this.redirectPath = typeof path === 'string' && path.startsWith('/') ? path : '/';
    },
    setCategories(boardType, categories) {
      this.categoryCache[boardType] = categories;
    },
  },
  getters: {
    getMemberName: (state) => state.memberName,
    getMemberId: (state) => state.memberId,
    isLoggedIn: (state) => !!state.memberId,
    getRedirectPath: (state) => state.redirectPath,
    getCategories: (state) => (boardType) => state.categoryCache[boardType] ?? null,
  },
});
