import { reactive } from 'vue';
import { useRoute } from 'vue-router';

export const toDateString = (date) => {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(date);
};

const defaultStartDate = () => {
  const d = new Date();
  d.setFullYear(d.getFullYear() - 1);
  return toDateString(d);
};

const defaultEndDate = () => toDateString(new Date());

export function useSearch(extras = {}) {
  const route = useRoute();
  return reactive({
    startDate: route.query.startDate || defaultStartDate(),
    endDate: route.query.endDate || defaultEndDate(),
    searchWord: route.query.searchWord || '',
    page: route.query.page || 1,
    limit: 10,
    orderByField: 'createdDate',
    direction: 'DESC',
    ...extras,
  });
}
