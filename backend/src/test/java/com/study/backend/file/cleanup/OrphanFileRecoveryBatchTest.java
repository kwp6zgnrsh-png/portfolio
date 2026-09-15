package com.study.backend.file.cleanup;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import com.study.backend.file.cleanup.model.OrphanFileCandidate;
import com.study.backend.file.cleanup.model.OrphanFileCandidate.Reason;
import com.study.backend.file.cleanup.service.OrphanFileRecoveryBatch;
import com.study.backend.file.cleanup.service.OrphanFileRecoveryService;
import com.study.backend.file.cleanup.service.OrphanFileScanner;

@ExtendWith(MockitoExtension.class)
class OrphanFileRecoveryBatchTest {

	@Mock
	OrphanFileScanner scanner;

	@Mock
	OrphanFileRecoveryService recoveryService;

	@InjectMocks
	OrphanFileRecoveryBatch batch;

	@Test
	void recoverCandidates_processesSequentially() throws Exception {
		OrphanFileCandidate first = candidate("first");
		OrphanFileCandidate second = candidate("second");

		given(scanner.scan()).willReturn(List.of(first, second));
		given(recoveryService.enqueueMoveIfStillCandidate(first))
			.willReturn(true);
		given(recoveryService.enqueueMoveIfStillCandidate(second))
			.willReturn(true);

		batch.recoverCandidates();

		InOrder order = inOrder(scanner, recoveryService);

		order.verify(scanner).scan();
		order.verify(recoveryService).enqueueMoveIfStillCandidate(first);
		order.verify(recoveryService).enqueueMoveIfStillCandidate(second);
	}

	@Test
	void recoverCandidates_changedCandidate_continues() throws Exception {
		OrphanFileCandidate first = candidate("first");
		OrphanFileCandidate second = candidate("second");

		given(scanner.scan()).willReturn(List.of(first, second));

		given(recoveryService.enqueueMoveIfStillCandidate(first))
			.willReturn(false);
		given(recoveryService.enqueueMoveIfStillCandidate(second))
			.willReturn(true);

		batch.recoverCandidates();

		then(recoveryService).should()
			.enqueueMoveIfStillCandidate(second);
	}

	@Test
	void recoverCandidates_databaseFailure_stopsRemainingCandidates()
		throws Exception {

		OrphanFileCandidate first = candidate("first");
		OrphanFileCandidate second = candidate("second");
		OrphanFileCandidate third = candidate("third");

		DataAccessResourceFailureException failure = new DataAccessResourceFailureException("DB 장애");

		given(scanner.scan()).willReturn(List.of(first, second, third));
		given(recoveryService.enqueueMoveIfStillCandidate(first))
			.willReturn(true);
		given(recoveryService.enqueueMoveIfStillCandidate(second))
			.willThrow(failure);

		assertThatThrownBy(() -> batch.recoverCandidates())
			.isSameAs(failure);

		then(recoveryService).should()
			.enqueueMoveIfStillCandidate(first);

		then(recoveryService).should(never())
			.enqueueMoveIfStillCandidate(third);
	}

	@Test
	void recoverCandidates_scanFailure_doesNotRegisterTasks()
		throws Exception {

		DataAccessResourceFailureException failure =
			new DataAccessResourceFailureException("조회 실패");

		given(scanner.scan()).willThrow(failure);

		assertThatThrownBy(() -> batch.recoverCandidates())
			.isSameAs(failure);

		then(recoveryService).shouldHaveNoInteractions();
	}

	private OrphanFileCandidate candidate(String name) {
		return new OrphanFileCandidate(
			"gallery/" + name + ".jpeg",
			10L,
			Instant.parse("2026-01-01T00:00:00Z"),
			Reason.UNREFERENCED
		);
	}
}