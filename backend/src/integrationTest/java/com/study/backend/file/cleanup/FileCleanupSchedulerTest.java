package com.study.backend.file.cleanup;

import static org.mockito.BDDMockito.*;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import com.study.backend.file.cleanup.mapper.FileCleanupTaskMapper;
import com.study.backend.file.cleanup.model.FileCleanupTask;
import com.study.backend.file.cleanup.model.FileCleanupTask.TaskType;
import com.study.backend.file.cleanup.scheduler.FileCleanupScheduler;
import com.study.backend.file.cleanup.service.FileCleanupCompletionService;
import com.study.backend.file.cleanup.service.FileCleanupWorker;
import com.study.backend.file.cleanup.service.OrphanFileRecoveryBatch;

@ExtendWith(MockitoExtension.class)
class FileCleanupSchedulerTest {

	@Mock
	FileCleanupCompletionService completionService;

	@Mock
	FileCleanupTaskMapper taskMapper;

	@Mock
	FileCleanupWorker worker;

	@Mock
	OrphanFileRecoveryBatch recoveryBatch;

	@InjectMocks
	FileCleanupScheduler scheduler;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(
			scheduler,
			"orphanRecoveryEnabled",
			true
		);
	}

	@Test
	@DisplayName("고아 탐지의 파일 접근 오류가 발생해도 기존 삭제 작업을 실행한다")
	void processDueTasks_scanIoFailure_executesExistingTask()
		throws Exception {

		FileCleanupTask task = FileCleanupTask.builder()
			.id(1L)
			.taskType(TaskType.DELETE)
			.sourcePath("gallery/deleted/example.jpeg")
			.build();

		willThrow(new IOException("탐색 폴더 접근 실패"))
			.given(recoveryBatch)
			.recoverCandidates();

		given(taskMapper.findDueTasks(50))
			.willReturn(List.of(task));

		scheduler.processDueTasks();

		then(taskMapper).should().findDueTasks(50);
		then(worker).should().execute(task);
		then(completionService).should().complete(task);
	}

	@Test
	@DisplayName("고아 복구 중 DB 오류가 발생하면 기존 작업도 실행하지 않는다")
	void processDueTasks_recoveryDbFailure_stopsBatch()
		throws Exception {

		willThrow(
			new DataAccessResourceFailureException("DB 조회 실패")
		)
			.given(recoveryBatch)
			.recoverCandidates();

		scheduler.processDueTasks();

		then(taskMapper).shouldHaveNoInteractions();
		then(worker).shouldHaveNoInteractions();
		then(completionService).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("기존 작업 조회가 실패하면 파일 작업을 실행하지 않는다")
	void processDueTasks_taskQueryFailure_stopsBatch() {
		given(taskMapper.findDueTasks(50))
			.willThrow(
				new DataAccessResourceFailureException("작업 조회 실패")
			);

		scheduler.processDueTasks();

		then(worker).shouldHaveNoInteractions();
		then(completionService).shouldHaveNoInteractions();
	}
}