package com.study.backend.board.strategy;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.study.backend.board.assembler.FreeBoardResponseAssembler;
import com.study.backend.board.dto.common.BoardCreateResult;
import com.study.backend.board.dto.common.request.BoardUpdateRequest;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;
import com.study.backend.board.model.Page;
import com.study.backend.board.model.Search;
import com.study.backend.board.service.FreeBoardService;
import com.study.backend.file.exception.FileException;
import com.study.backend.file.exception.FileStorageException;
import com.study.backend.file.service.FileServiceFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service("boardsStrategy")
@RequiredArgsConstructor
public class FreeBoardStrategy implements BoardReadableStrategy, BoardCreateStrategy, BoardUpdateStrategy, BoardDeleteStrategy {

	private final FreeBoardService boardService;
	private final FreeBoardResponseAssembler responseAssembler;
	private final FileServiceFactory fileService;

	@Override
	public List<Board> searchPostList(Search search) {
		return boardService.searchPostList(search, BoardType.BOARDS.id());
	}

	@Override
	public Integer getPostCount(Search search) {
		return boardService.getPostCountByCriteria(search, BoardType.BOARDS.id());
	}

	@Override
	public Object assembleListResponse(List<Board> boardList, Page page) {
		return responseAssembler.assembleListResponse(boardList, page);
	}

	/** 파일 검증·저장 실패 시 게시글은 유지하고 파일 업로드 실패를 알린다. */
	@Override
	public BoardCreateResult createPost(Board board, Long boardTypeId, Long memberId, MultipartFile[] files) {
		boardService.createPost(board, boardTypeId, memberId);

		if (files != null && files.length > 0) {
			try {
				fileService.getFileService(BoardType.BOARDS).validateFiles(files);
				boardService.createFiles(board.getId(), files);
			} catch (FileException | FileStorageException e) {
				log.warn("게시글은 등록됐지만 파일 저장 실패: boardId={}", board.getId(), e);
				return BoardCreateResult.withFileUploadFailure();
			}
		}
		return BoardCreateResult.success();
	}

	@Override
	public void updateViews(Long boardId) {
		boardService.updateViews(boardId);
	}

	@Override
	public Board getPostById(Long boardId) {
		return boardService.getPostById(boardId);
	}

	@Override
	public Object assembleDetailResponse(Board board, Long memberId) {
		return responseAssembler.assembleDetailResponse(board, memberId);
	}

	@Override
	public Object assembleUpdateFormResponse(Board board) {
		return responseAssembler.assembleUpdateFormResponse(board);
	}

	@Override
	public Board getPostForUpdate(Long boardId, Long memberId) {
		return boardService.getPostForUpdate(boardId, memberId);
	}

	@Override
	public void updatePost(Long boardId, BoardUpdateRequest board, Long memberId, MultipartFile[] file) {
		boardService.updatePost(boardId, board, memberId, file);
	}

	@Override
	public void deletePost(Long boardId, Long memberId) {
		boardService.deletePost(boardId, memberId);
	}

	@Override
	public List<Board> getPostList() {
		return boardService.getPostList();
	}
}
