<script setup>
import { debounce } from "../composables/useDebounce.js";
import { toDateString } from "../composables/useSearch.js";

const emit = defineEmits(["searchRequested"]);

/* props */
const props = defineProps({
  categories: {
    type: Array,
    default: () => []
  },
  search: {
    type: Object,
    required: true
  },
  listApiPath: {
    type: Object,
    required: true
  },
});

const updateList = debounce(() => {
  props.search.page = 1;
  emit("searchRequested");
}, 300);

const dateValid = (click) =>{
  const startDate = new Date(props.search.startDate);
  const endDate = new Date(props.search.endDate);

  if(startDate > endDate){
    alert("시작 날짜는 종료 날짜보다 이전이어야 합니다");
    return;
  }

  const millisecondsPerYear = 365 * (24 * 60 * 60 * 1000);
  const dateDifference = Math.abs(startDate - endDate);

  if (dateDifference > millisecondsPerYear) {
    alert("최대 검색 범위는 1년입니다");
    updateDateRange(click, endDate, startDate, millisecondsPerYear);
  }
}

const updateDateRange = (click, endDate, startDate, millisecondsPerYear) => {
  if(click === 'startDate'){
    props.search.startDate = toDateString(new Date(endDate.getTime() - millisecondsPerYear));
  }
  if(click === 'endDate'){
    props.search.endDate = toDateString(new Date(startDate.getTime() + millisecondsPerYear));
  }
}

const inputWidthClass = () =>{
  return props.listApiPath.path === '/inquiries' ? 'w-2/5' : 'w-1/5';
}
</script>

<template>
  <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-4">
    <div class="flex flex-wrap items-center gap-2">
      <input type="date"
             @change="dateValid('startDate')"
             v-model="search.startDate"
             class="border border-gray-200 rounded-lg px-3 py-1.5 text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-100">
      <span class="text-gray-400 text-sm">~</span>
      <input type="date"
             @change="dateValid('endDate')"
             v-model="search.endDate"
             class="border border-gray-200 rounded-lg px-3 py-1.5 text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-100">
      <select v-if="listApiPath.path !== '/inquiries'" v-model="search.categoryId"
              class="border border-gray-200 rounded-lg px-3 py-1.5 text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-100">
        <option value="">전체 분류</option>
        <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
      </select>
      <input type="text" v-model="search.searchWord" placeholder="검색어를 입력해주세요"
             @keyup.enter="updateList"
             class="flex-1 min-w-[160px] border border-gray-200 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-100">
      <button type="button" @click="updateList"
              class="bg-blue-500 hover:bg-blue-600 text-white text-sm font-medium px-4 py-1.5 rounded-lg transition-colors">
        검색
      </button>
    </div>

    <div class="flex items-center justify-between mt-3 pt-3 border-t border-gray-100">
      <div class="flex items-center gap-2 text-sm text-gray-500">
        <select v-model="search.limit" @change="updateList"
                class="border border-gray-200 rounded-lg px-2 py-1 text-sm text-gray-600 focus:outline-none">
          <option value="10">10</option>
          <option value="20">20</option>
          <option value="30">30</option>
          <option value="40">40</option>
          <option value="50">50</option>
        </select>
        <span>개씩 보기</span>
      </div>
      <div class="flex items-center gap-2 text-sm text-gray-500">
        <span>정렬</span>
        <select v-model="search.orderByField" @change="updateList"
                class="border border-gray-200 rounded-lg px-2 py-1 text-sm text-gray-600 focus:outline-none">
          <option value="createdDate">등록일시</option>
          <option value="categoryName">분류</option>
          <option value="title">제목</option>
          <option value="views">조회수</option>
        </select>
        <select v-model="search.direction" @change="updateList"
                class="border border-gray-200 rounded-lg px-2 py-1 text-sm text-gray-600 focus:outline-none">
          <option value="DESC">내림차순</option>
          <option value="ASC">오름차순</option>
        </select>
      </div>
    </div>
  </div>
</template>

<style scoped>

</style>
