package com.study.backend.board.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.study.backend.board.dto.common.request.BoardUpdateRequest;
import com.study.backend.board.exception.BoardConflictException;
import com.study.backend.board.mapper.FreeBoardMapper;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;
import com.study.backend.category.service.CategoryService;
import com.study.backend.file.service.FileService;
import com.study.backend.file.service.FileServiceFactory;
import com.study.backend.file.util.FileChangeUtils;

@Service
public class FreeBoardService extends AbstractBoardService<FreeBoardMapper> {

	private final FileServiceFactory fileService;
	private final CategoryService categoryService;

	public FreeBoardService(FreeBoardMapper mapper, FileServiceFactory fileService, CategoryService categoryService) {
		super(mapper);
		this.fileService = fileService;
		this.categoryService = categoryService;
	}

	/** 자유게시판 게시글을 등록한다. */
	@Transactional
	public void createPost(Board board, Long boardTypeId, Long memberId) {
		categoryService.validateCategory(board.getCategoryId(), boardType().categoryType());
		mapper.createPost(board, boardTypeId, memberId);
	}

	/** 자유게시판 게시글에 첨부 파일을 저장한다. */
	@Transactional
	public void createFiles(Long boardId, MultipartFile[] files) {
		fileService.getFileService(BoardType.BOARDS).createFiles(boardId, files);
	}

	/** 기존 파일을 삭제하고 새 파일과 함께 게시글 내용을 수정한다. 작성자 본인만 가능하다. */
	@Transactional
	public void updatePost(Long boardId, BoardUpdateRequest board, Long memberId, MultipartFile[] files) {
		Board updateBoard = mapper.getPostById(boardId);
		validateOwnership(updateBoard, memberId, "수정할 수 있는 권한이 없습니다.");
		categoryService.validateCategory(board.getCategoryId(), boardType().categoryType());

		boolean hasNewFiles = FileChangeUtils.hasNewFiles(files);
		boolean hasDeletedFiles = FileChangeUtils.hasDeletedFiles(board.getDeleteFiles());
		if (!hasNewFiles && !hasDeletedFiles) {
			updateBoardData(boardId, board, memberId);
			return;
		}

		FileService fs = fileService.getFileService(BoardType.BOARDS);

		fs.validateFileCountForUpdate(
			boardId,
			board.getDeleteFiles(),
			files
		);

		fs.deleteFiles(boardId, board.getDeleteFiles());

		if (hasNewFiles) {
			fs.createFiles(boardId, files);
		}

		updateBoardData(boardId, board, memberId);
	}

	/** 수정 폼에 필요한 게시글을 조회한다. 존재하지 않거나 작성자가 아니면 예외를 던진다. */
	public Board getPostForUpdate(Long boardId, Long memberId) {
		Board board = mapper.getPostForUpdate(boardId);
		validateOwnership(board, memberId, "수정할 수 없습니다.");
		return board;
	}

	/** 게시글을 삭제한다. 작성자 본인만 가능하다. */
	@Transactional
	public void deletePost(Long boardId, Long memberId) {
		Board board = mapper.getPostById(boardId);
		validateOwnership(board, memberId, "삭제할 수 있는 권한이 없습니다.");

		int affectedRows = mapper.deletePost(boardId, memberId);
		if (affectedRows != 1) {
			throw new BoardConflictException("이미 삭제되었거나 상태가 변경된 게시글입니다.");

		}
		fileService.getFileService(BoardType.BOARDS).deleteAllFilesByBoardId(boardId);
	}

	private void updateBoardData(Long boardId, BoardUpdateRequest board, Long memberId) {
		int affectedRows = mapper.updatePost(boardId, board, memberId);

		if (affectedRows != 1) {
			throw new BoardConflictException("게시글 상태가 변경되어 수정할 수 없습니다.");
		}
	}

	@Override
	protected BoardType boardType() {
		return BoardType.BOARDS;
	}
}
