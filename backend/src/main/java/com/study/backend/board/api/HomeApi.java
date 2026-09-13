package com.study.backend.board.api;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.study.backend.board.converter.BoardConverter;
import com.study.backend.board.dto.home.response.HomeResponse;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;
import com.study.backend.board.strategy.BoardStrategyFactory;
import com.study.backend.common.annotation.Public;
import com.study.backend.common.dto.ApiResponse;
import com.study.backend.common.util.BoardQueryRunner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class HomeApi {

	private final BoardStrategyFactory boardStrategyFactory;
	private final BoardConverter boardConverter;
	private final BoardQueryRunner boardQueryRunner;

	/**
	 * 홈 화면에 표시할 공지사항, 자유게시판, 갤러리, 문의사항 목록을 비동기로 조회한다.
	 * 각 게시판 영역은 독립적으로 조회한다.
	 * 일부 조회가 실패하면 해당 영역만 빈 목록으로 반환하고 나머지는 정상 제공한다.
	 */
	@Public
	@GetMapping("/home")
	public ApiResponse<?> home() {
		CompletableFuture<List<Board>> noticesFuture = boardQueryRunner
			.submit(()-> boardStrategyFactory
				.requireReadableStrategy(BoardType.NOTICES.name())
				.getPostList())
			.exceptionally(e -> {
				log.error("notices 조회 실패", e);
				return List.of();
			});
		CompletableFuture<List<Board>> boardsFuture = boardQueryRunner
			.submit(() -> boardStrategyFactory
				.requireReadableStrategy(BoardType.BOARDS.name())
				.getPostList())
			.exceptionally(e -> {
				log.error("boards 조회 실패", e);
				return List.of();
			});

		CompletableFuture<List<Board>> galleriesFuture = boardQueryRunner
			.submit(() -> boardStrategyFactory
				.requireReadableStrategy(BoardType.GALLERIES.name())
				.getPostList())
			.exceptionally(e -> {
				log.error("galleries 조회 실패", e);
				return List.of();
			});

		CompletableFuture<List<Board>> inquiriesFuture = boardQueryRunner
			.submit(() -> boardStrategyFactory
				.requireReadableStrategy(BoardType.INQUIRIES.name())
				.getPostList())
			.exceptionally(e -> {
				log.error("inquiries 조회 실패", e);
				return List.of();
			});

		CompletableFuture.allOf(noticesFuture, boardsFuture, galleriesFuture, inquiriesFuture).join();

		HomeResponse response = HomeResponse.builder()
			.noticeList(noticesFuture.join().stream().map(boardConverter::convertToHomeNotice).toList())
			.freeBoardList(boardsFuture.join().stream().map(boardConverter::convertToHomeFreeBoard).toList())
			.galleryList(galleriesFuture.join().stream().map(boardConverter::convertToHomeGallery).toList())
			.inquiryList(inquiriesFuture.join().stream().map(boardConverter::convertToHomeInquiry).toList())
			.build();

		return ApiResponse.of("성공", response);
	}
}
