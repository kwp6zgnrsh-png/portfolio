package com.study.backend.board.strategy;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.study.backend.board.assembler.GalleryResponseAssembler;
import com.study.backend.board.dto.common.BoardCreateResult;
import com.study.backend.board.dto.common.request.BoardUpdateRequest;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;
import com.study.backend.board.model.Page;
import com.study.backend.board.model.Search;
import com.study.backend.board.service.GalleryService;
import com.study.backend.file.exception.FileException;
import com.study.backend.file.exception.RequiredFileException;

import lombok.RequiredArgsConstructor;

@Service("galleriesStrategy")
@RequiredArgsConstructor
public class GalleryStrategy implements BoardReadableStrategy, BoardCreateStrategy, BoardUpdateStrategy, BoardDeleteStrategy {

	private final GalleryService boardService;
	private final GalleryResponseAssembler responseAssembler;

	@Override
	public List<Board> searchPostList(Search search) {
		return boardService.searchPostList(search, BoardType.GALLERIES.id());
	}

	@Override
	public Integer getPostCount(Search search) {
		return boardService.getPostCountByCriteria(search, BoardType.GALLERIES.id());
	}

	@Override
	public Object assembleListResponse(List<Board> boardList, Page page) {
		return responseAssembler.assembleListResponse(boardList, page);
	}

	@Override
	public BoardCreateResult createPost(Board board, Long boardTypeId, Long memberId, MultipartFile[] files) {
		if (files == null || files.length == 0) {
			throw new RequiredFileException("갤러리 게시글에는 이미지가 필요합니다.");
		}

		try {
			boardService.createPostWithFilesAndThumbnail(board, boardTypeId, memberId, files);
		} catch (FileException e) {
			throw new FileException("파일을 다시 업로드해주세요.", e);
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
