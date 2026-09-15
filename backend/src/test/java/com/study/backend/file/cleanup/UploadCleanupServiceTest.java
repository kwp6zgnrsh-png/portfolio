package com.study.backend.file.cleanup;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.study.backend.file.cleanup.model.FileCleanupTask;
import com.study.backend.file.cleanup.model.FileCleanupTask.TaskType;
import com.study.backend.file.cleanup.service.FileCleanupTaskService;
import com.study.backend.file.cleanup.service.FileCleanupWorker;
import com.study.backend.file.cleanup.service.UploadCleanupService;

@ExtendWith(MockitoExtension.class)
class UploadCleanupServiceTest {

	@TempDir
	Path tempDir;

	@Mock
	FileCleanupWorker worker;

	@Mock
	FileCleanupTaskService taskService;

	private UploadCleanupService cleanupService;
	private Path storageRoot;
	private Path uploadedFile;
	private String relativePath;

	@BeforeEach
	void setUp() {
		storageRoot = tempDir.resolve("storage");
		uploadedFile = storageRoot.resolve("gallery/example.jpeg");
		relativePath = Path.of("gallery", "example.jpeg").toString();

		cleanupService = new UploadCleanupService(
			worker,
			taskService,
			storageRoot.toString()
		);
	}

	@Test
	@DisplayName("즉시 삭제에 성공하면 재시도 작업을 등록하지 않는다")
	void cleanupFiles_success_doesNotEnqueueRetry() throws Exception {
		cleanupService.cleanupFiles(List.of(uploadedFile));

		ArgumentCaptor<FileCleanupTask> captor = ArgumentCaptor.forClass(FileCleanupTask.class);

		verify(worker).execute(captor.capture());

		FileCleanupTask task = captor.getValue();

		assertThat(task.getTaskType()).isEqualTo(TaskType.DELETE);
		assertThat(task.getSourcePath()).isEqualTo(relativePath);
		assertThat(task.getDestinationPath()).isNull();

		verifyNoInteractions(taskService);
	}

	@Test
	@DisplayName("즉시 삭제에 실패하면 별도 저장용 메서드로 재시도를 등록한다")
	void cleanupFiles_deleteFails_enqueuesRetry() throws Exception {
		doThrow(new IOException("삭제 실패"))
			.when(worker)
			.execute(any(FileCleanupTask.class));

		cleanupService.cleanupFiles(List.of(uploadedFile));

		verify(taskService).enqueueFailedUploadDelete(relativePath);
	}

	@Test
	@DisplayName("삭제와 작업 등록이 모두 실패해도 예외를 전파하지 않는다")
	void cleanupFiles_registrationFails_doesNotThrow() throws Exception {
		doThrow(new IOException("삭제 실패"))
			.when(worker)
			.execute(any(FileCleanupTask.class));

		doThrow(new IllegalStateException("DB 저장 실패"))
			.when(taskService)
			.enqueueFailedUploadDelete(relativePath);

		assertThatCode(
			() -> cleanupService.cleanupFiles(List.of(uploadedFile))
		).doesNotThrowAnyException();

		verify(taskService).enqueueFailedUploadDelete(relativePath);
	}

	@Test
	@DisplayName("저장소 밖 경로는 삭제하거나 작업으로 등록하지 않는다")
	void cleanupFiles_outsideStorage_doesNothing() {
		Path outsideFile = tempDir.resolve("outside.jpeg");

		cleanupService.cleanupFiles(List.of(outsideFile));

		verifyNoInteractions(worker, taskService);
	}
}