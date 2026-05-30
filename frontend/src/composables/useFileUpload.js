import {reactive, onUnmounted} from 'vue';
import fileService from '../services/fileService.js';

export function useFileUpload({ maxFiles = 10, withPreview = false } = {}) {
  const fileState = reactive({
    fileCount: 1,
    files: [],
    removeFiles: [],
    uploadFiles: [],
    previewUrls: [],
  });

  const addFileInput = () => {
    if (fileState.fileCount + fileState.files.length >= maxFiles) {
      alert(`파일은 최대 ${maxFiles}개까지 추가 가능`);
      return;
    }
    fileState.fileCount++;
  };

  const deleteExistingFile = (fileId, index) => {
    if (confirm("삭제하시겠습니까?")) {
      fileState.removeFiles.push(fileId);
      fileState.files.splice(index, 1);
      if (withPreview) fileState.previewUrls.splice(index, 1);
      addFileInput();
    }
  };

  const handleFileChange = (event, index, previewIndex = index) => {
    const file = event.target.files[0];

    if (withPreview) {
      const oldUrl = fileState.previewUrls[previewIndex];
      if (oldUrl?.startsWith('blob:')) URL.revokeObjectURL(oldUrl);
    }

    if (file) {
      fileState.uploadFiles[index] = file;
      if (withPreview) {
        fileState.previewUrls[previewIndex] = URL.createObjectURL(file);
      }
    } else {
      fileState.uploadFiles.splice(index, 1);
      if (withPreview) {
        fileState.previewUrls.splice(previewIndex, 1);
      }
    }
  };


  const setExistingFiles = (files) => {
    fileState.files = files;
    fileState.fileCount = files.length >= maxFiles ? 0 : 1;
    if (withPreview) {
      files.forEach(file => {
        fileState.previewUrls.push(fileService.getImageUrl(file.path, file.storeName, file.extension));
      });
    }
  };

  if (withPreview) {
    onUnmounted(() => {
      fileState.previewUrls
        .filter(url => url.startsWith('blob:'))
        .forEach(url => URL.revokeObjectURL(url));
    });
  }

  return { fileState, addFileInput, deleteExistingFile, handleFileChange, setExistingFiles };
}
