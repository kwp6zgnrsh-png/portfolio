package com.study.backend.board.api;

import java.util.List;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.study.backend.board.converter.BoardConverter;
import com.study.backend.board.dto.common.BoardCreateResult;
import com.study.backend.board.dto.common.request.BoardCreateRequest;
import com.study.backend.board.dto.common.request.BoardUpdateRequest;
import com.study.backend.board.dto.common.request.SearchRequest;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;
import com.study.backend.board.model.Page;
import com.study.backend.board.model.Search;
import com.study.backend.board.strategy.BoardCreateStrategy;
import com.study.backend.board.strategy.BoardDeleteStrategy;
import com.study.backend.board.strategy.BoardReadableStrategy;
import com.study.backend.board.strategy.BoardStrategyFactory;
import com.study.backend.board.strategy.BoardUpdateStrategy;
import com.study.backend.common.annotation.LoginMember;
import com.study.backend.common.annotation.Public;
import com.study.backend.common.dto.ApiResponse;
import com.study.backend.common.util.Pagination;
import com.study.backend.file.service.FileServiceFactory;
import com.study.backend.file.util.FileChangeUtils;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class BoardApi {

	private final BoardStrategyFactory boardStrategyFactory;
	private final FileServiceFactory fileServiceFactory;
	private final BoardConverter boardConverter;
	private final Pagination paginationService;

	/** 게시판 타입과 검색 조건을 받아 게시글 목록과 페이지 정보를 반환한다. */
	@Public
	@GetMapping("/{boardType}")
	public ApiResponse<?> searchBoardList(@PathVariable String boardType,
									      @Valid @ModelAttribute SearchRequest searchRequest,
									      @LoginMember(required = false) Long memberId) {

		BoardReadableStrategy boardService = boardStrategyFactory.requireReadableStrategy(boardType);

		Search search = boardConverter.convertToSearch(searchRequest);
		search.setMemberId(memberId);

		Integer postCount = boardService.getPostCount(search);
		Page page = paginationService.pagination(postCount, search.getPage(), search.getLimit());
		Search normalizedSearch = search.toBuilder()
			.page(page.getCurrentPage())
			.build();

		List<Board> searchPostList = boardService.searchPostList(normalizedSearch);
		Object response = boardService.assembleListResponse(searchPostList, page);

		return ApiResponse.of("성공", response);
	}

	/** 게시글을 생성하고, 파일이 있으면 함께 저장한다. */
	@PostMapping("/{boardType}")
	public ApiResponse<?> createBoard(@PathVariable String boardType,
								      @ModelAttribute @Valid BoardCreateRequest createRequest,
								      @RequestPart(value = "file", required = false) MultipartFile[] files,
								      @LoginMember Long memberId) {

		BoardCreateStrategy boardService = boardStrategyFactory.requireCreateStrategy(boardType);
		Board board = boardConverter.convertToBoard(createRequest);

		validateRequiredFilesBeforeCreate(boardType, files);

		BoardCreateResult result = boardService.createPost(board, BoardType.idOf(boardType), memberId, files);

		return ApiResponse.of(result.message());
	}

	/** 게시글 상세 정보를 조회하고 조회수를 증가시킨다. 비밀글은 secret_token 쿠키로 접근을 검증한다. */
	@Public
	@GetMapping("/{boardType}/{id}")
	public ApiResponse<?> boardDetail(@PathVariable String boardType,
								      @PathVariable("id") Long boardId,
								      @LoginMember(required = false) Long memberId,
								      @CookieValue(value = "secret_token", required = false) String secretToken) {

		BoardReadableStrategy boardService = boardStrategyFactory.requireReadableStrategy(boardType);
		Board findPost = boardService.getPostById(boardId);

		boardStrategyFactory.validateAccess(boardType, findPost, memberId, secretToken);
		boardService.updateViews(boardId);
		findPost.setViews(findPost.getViews() + 1);

		Object response = boardService.assembleDetailResponse(findPost, memberId);

		return ApiResponse.of("성공", response);
	}

	/** 수정 폼에 필요한 게시글 정보를 반환한다. 작성자 본인만 접근 가능하다. */
	@GetMapping("/{boardType}/update/{id}")
	public ApiResponse<?> getUpdateForm(@PathVariable("id") Long boardId,
								        @PathVariable String boardType,
								        @LoginMember Long memberId) {

		BoardUpdateStrategy boardService = boardStrategyFactory.requireUpdateStrategy(boardType);
		Board board = boardService.getPostForUpdate(boardId, memberId);
		Object response = boardService.assembleUpdateFormResponse(board);

		return ApiResponse.of("성공", response);
	}

	/** 게시글 내용과 파일을 수정한다. */
	@PutMapping("/{boardType}/{id}")
	public ApiResponse<?> updateBoard(@PathVariable String boardType,
								      @PathVariable("id") Long boardId,
								      @ModelAttribute @Valid BoardUpdateRequest boardUpdate,
								      @RequestPart(value = "file", required = false) MultipartFile[] files,
								      @LoginMember Long memberId) {

		BoardUpdateStrategy boardService = boardStrategyFactory.requireUpdateStrategy(boardType);

		validateFilesIfPresent(boardType, files);

		boardService.updatePost(boardId, boardUpdate, memberId, files);

		return ApiResponse.of("수정 완료");
	}

	/** 게시글을 삭제한다. */
	@DeleteMapping("/{boardType}/{id}")
	public ApiResponse<?> removeBoard(@PathVariable String boardType,
								      @PathVariable("id") Long boardId,
								      @LoginMember Long memberId) {

		BoardDeleteStrategy boardService = boardStrategyFactory.requireDeleteStrategy(boardType);
		boardService.deletePost(boardId, memberId);

		return ApiResponse.of("삭제 완료");
	}

	/** 생성 요청의 첨부 파일 필수 여부와 파일 유효성을 게시판 타입에 맞게 검증한다. */
	private void validateRequiredFilesBeforeCreate(String boardType, MultipartFile[] files) {
		if (BoardType.BOARDS == BoardType.from(boardType)) {
			return;
		}
		validateFilesIfPresent(boardType, files);
	}

	/** 비어 있지 않은 첨부 파일이 있을 때만 파일 정책을 검증한다. */
	private void validateFilesIfPresent(String boardType, MultipartFile[] files) {
		if (!FileChangeUtils.hasNewFiles(files)) {
			return;
		}

		fileServiceFactory
			.getFileService(boardType)
			.validateFiles(files);
	}
}
