package com.study.backend.board.service;

import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.study.backend.board.dto.common.request.BoardUpdateRequest;
import com.study.backend.board.exception.BoardConflictException;
import com.study.backend.board.mapper.GalleryMapper;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;
import com.study.backend.category.service.CategoryService;
import com.study.backend.file.event.FileCleanupEvent;
import com.study.backend.file.exception.FileException;
import com.study.backend.file.model.FileMetaData;
import com.study.backend.file.service.FileService;
import com.study.backend.file.service.FileServiceFactory;
import com.study.backend.file.util.FileChangeUtils;
import com.study.backend.file.util.PathUtils;
import com.study.backend.thumbnail.dto.SourceImage;
import com.study.backend.thumbnail.model.ThumbnailMetaData;
import com.study.backend.thumbnail.service.ThumbnailService;

@Service
public class GalleryService extends AbstractBoardService<GalleryMapper> {

	@Value("${store.base-path}")
	private String storePath;

	private final FileServiceFactory fileService;
	private final ThumbnailService thumbnailService;
	private final ApplicationEventPublisher eventPublisher;
	private final CategoryService categoryService;

	public GalleryService(GalleryMapper mapper, FileServiceFactory fileService, ThumbnailService thumbnailService,
						  ApplicationEventPublisher eventPublisher, CategoryService categoryService) {
		super(mapper);
		this.fileService = fileService;
		this.thumbnailService = thumbnailService;
		this.eventPublisher = eventPublisher;
		this.categoryService = categoryService;
	}

	/** 갤러리 게시글을 등록한다. */
	@Transactional
	public void createPost(Board board, Long boardTypeId, Long memberId) {
		categoryService.validateCategory(board.getCategoryId(), boardType().categoryType());
		mapper.createPost(board, boardTypeId, memberId);
	}

	/** 갤러리 게시글, 원본 이미지, 썸네일을 하나의 트랜잭션으로 등록한다. */
	@Transactional
	public void createPostWithFilesAndThumbnail(Board board, Long boardTypeId, Long memberId, MultipartFile[] files) {
		categoryService.validateCategory(
			board.getCategoryId(),
			boardType().categoryType()
		);

		mapper.createPost(board, boardTypeId, memberId);

		createFilesAndThumbnail(board.getId(), files);
	}

	/** 파일 저장 + 썸네일 생성은 하나의 트랜잭션 — 썸네일 실패 시 파일 메타데이터도 롤백한다. */
	@Transactional
	public void createFilesAndThumbnail(Long boardId, MultipartFile[] files) {
		FileService fs = fileService.getFileService(BoardType.GALLERIES);
		fs.createFiles(boardId, files);

		FileMetaData firstFile = fs.getFirstFileByBoardId(boardId);
		if (firstFile == null) {
			throw new FileException("파일 저장에 실패했습니다.");
		}

		thumbnailService.saveThumbnail(buildThumbnail(firstFile), boardId);
	}

	/** 기존 파일·썸네일을 교체하고 게시글을 수정한다. 신규 파일의 롤백 정리는 생성 서비스가 담당한다. */
	@Transactional
	public void updatePost(Long boardId, BoardUpdateRequest board, Long memberId, MultipartFile[] files) {
		Board updateBoard = mapper.getPostById(boardId);
		validateOwnership(
			updateBoard,
			memberId,
			"수정할 수 있는 권한이 없습니다."
		);

		categoryService.validateCategory(board.getCategoryId(), boardType().categoryType());

		boolean hasNewFiles = FileChangeUtils.hasNewFiles(files);
		boolean hasDeletedFiles = FileChangeUtils.hasDeletedFiles(board.getDeleteFiles());

		// 파일 변경이 없으면 게시글 데이터만 수정한다.
		if (!hasNewFiles && !hasDeletedFiles) {
			updateBoardData(boardId, board, memberId);
			return;
		}

		FileService fs = fileService.getFileService(BoardType.GALLERIES);

		ThumbnailMetaData oldThumbnail = thumbnailService.getThumbnailByBoardId(boardId);

		thumbnailService.deleteThumbnail(boardId);

		fs.validateFileCountForUpdate(
			boardId,
			board.getDeleteFiles(),
			files
		);

		fs.deleteFiles(boardId, board.getDeleteFiles());

		if (hasNewFiles) {
			fs.createFiles(boardId, files);
		}

		FileMetaData firstFile = fs.getFirstFileByBoardId(boardId);
		if (firstFile == null) {
			throw new FileException("파일 저장에 실패했습니다.");
		}

		Path thumbnailPath = thumbnailService.saveThumbnail(buildThumbnail(firstFile), boardId);
		publishThumbnailDeleteEventIfChanged(oldThumbnail, thumbnailPath);
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

		ThumbnailMetaData thumbnail = thumbnailService.getThumbnailByBoardId(boardId);

		int affectedRows = mapper.deletePost(boardId, memberId);
		if (affectedRows != 1) {
			throw new BoardConflictException("이미 삭제되었거나 상태가 변경된 게시글입니다.");

		}

		fileService.getFileService(BoardType.GALLERIES).deleteAllFilesByBoardId(boardId);
		thumbnailService.deleteThumbnail(boardId);

		if (thumbnail != null) {
			eventPublisher.publishEvent(FileCleanupEvent.forDelete(List.of(thumbnailFilePath(thumbnail))));
		}
	}

	/** 썸네일이 변경된 경우 이전 썸네일의 삭제 이벤트를 발행한다. */
	private void publishThumbnailDeleteEventIfChanged(ThumbnailMetaData oldThumbnail, Path newThumbnailPath) {
		if (oldThumbnail == null) {
			return;
		}

		String oldThumbnailPath = thumbnailFilePath(oldThumbnail);
		if (newThumbnailPath != null && oldThumbnailPath.equals(newThumbnailPath.toString())) {
			return;
		}
		eventPublisher.publishEvent(FileCleanupEvent.forDelete(List.of(oldThumbnailPath)));
	}

	/** 파일 메타데이터로 SourceImage 객체를 생성한다. */
	private SourceImage buildThumbnail(FileMetaData file) {
		return SourceImage.builder()
			.fileName(file.getFileName())
			.storeName(file.getStoreName())
			.path(file.getPath())
			.extension(file.getExtension())
			.build();
	}

	private void updateBoardData(Long boardId, BoardUpdateRequest board, Long memberId) {
		int affectedRows = mapper.updatePost(boardId, board, memberId);

		if (affectedRows != 1) {
			throw new BoardConflictException("게시글 상태가 변경되어 수정할 수 없습니다.");
		}
	}

	private String thumbnailFilePath(ThumbnailMetaData thumbnail) {
		return PathUtils.joinStoreFilePath(
			storePath,
			thumbnail.getPath(),
			thumbnail.getStoreName(),
			thumbnail.getExtension()
		);
	}

	@Override
	protected BoardType boardType() {
		return BoardType.GALLERIES;
	}
}
