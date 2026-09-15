package com.study.backend.file.cleanup.service;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Service;

import com.study.backend.file.cleanup.model.OrphanFileCandidate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrphanFileRecoveryBatch {

	private final OrphanFileScanner scanner;
	private final OrphanFileRecoveryService recoveryService;

	/**
	 * 후보를 순차적으로 재확인하고 MOVE 작업을 등록한다.
	 * 조회·등록 오류는 전파하여 남은 후보 처리를 중단한다.
	 * 전체 반복문을 하나의 트랜잭션으로 묶지 않는다.
	 */
	public void recoverCandidates() throws IOException {
		List<OrphanFileCandidate> candidates = scanner.scan();

		for (OrphanFileCandidate candidate : candidates) {
			boolean registered = recoveryService.enqueueMoveIfStillCandidate(candidate);

			if (registered) {
				log.info(
					"고아 파일 MOVE 등록: path={}, reason={}",
					candidate.relativePath(),
					candidate.reason()
				);
			} else {
				log.debug(
					"고아 파일 MOVE 등록 보류: path={}",
					candidate.relativePath()
				);
			}
		}
	}
}