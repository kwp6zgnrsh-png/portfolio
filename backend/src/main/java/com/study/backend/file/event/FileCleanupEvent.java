package com.study.backend.file.event;

import java.util.List;

public record FileCleanupEvent(List<FileMoveTask> moveTasks, List<String> deletePaths) {

    public record FileMoveTask(String srcPath, String destPath) {}

    /** 파일을 deleted 폴더로 이동할 때 사용한다 (사용자 업로드 파일). */
    public static FileCleanupEvent forMove(List<FileMoveTask> tasks) {
        return new FileCleanupEvent(tasks, List.of());
    }

    /** 파일을 단순 삭제할 때 사용한다 (썸네일 등 생성 파일). */
    public static FileCleanupEvent forDelete(List<String> paths) {
        return new FileCleanupEvent(List.of(), paths);
    }
}
