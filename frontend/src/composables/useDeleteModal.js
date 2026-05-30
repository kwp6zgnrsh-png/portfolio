import { reactive } from 'vue';

export function useDeleteModal() {
  const isOpen = reactive({ state: false });
  const open = () => { isOpen.state = true; };
  const close = () => { isOpen.state = false; };
  return { isOpen, open, close };
}
