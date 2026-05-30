package com.study.backend.board.strategy;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.study.backend.board.assembler.InquiryResponseAssembler;
import com.study.backend.board.dto.common.BoardCreateResult;
import com.study.backend.board.dto.common.request.BoardUpdateRequest;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;
import com.study.backend.board.model.Page;
import com.study.backend.board.model.Search;
import com.study.backend.board.service.InquiryService;
import com.study.backend.common.util.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@Service("inquiriesStrategy")
@RequiredArgsConstructor
public class InquiryStrategy implements BoardReadableStrategy, BoardCreateStrategy, BoardUpdateStrategy, BoardDeleteStrategy, BoardSecretStrategy {

	private final InquiryService boardService;
	private final InquiryResponseAssembler responseAssembler;
	private final JwtTokenProvider jwtTokenProvider;

	@Override
	public List<Board> searchPostList(Search search) {
		return boardService.searchPostList(search, BoardType.INQUIRIES.id());
	}

	@Override
	public Integer getPostCount(Search search) {
		return boardService.getPostCountByCriteria(search, BoardType.INQUIRIES.id());
	}

	@Override
	public Object assembleListResponse(List<Board> boardList, Page page) {
		return responseAssembler.assembleListResponse(boardList, page);
	}

	@Override
	public BoardCreateResult createPost(Board board, Long boardTypeId, Long memberId, MultipartFile[] files) {
		boardService.createPost(board, boardTypeId, memberId);
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
		boardService.updatePost(boardId, board, memberId);
	}

	@Override
	public void deletePost(Long boardId, Long memberId) {
		boardService.deletePost(boardId, memberId);
	}

	@Override
	public List<Board> getPostList() {
		return boardService.getPostList();
	}

	@Override
	public boolean matchesSecretPassword(Long boardId, String rawPassword) {
		return boardService.matchesSecretPassword(boardId, rawPassword);
	}

	@Override
	public boolean isPostOwner(Long boardId, Long memberId) {
		return boardService.isPostOwner(boardId, memberId);
	}

	@Override
	public void verifySecretPassword(Long boardId, String rawPassword) {
		boardService.verifySecretPassword(boardId, rawPassword);
	}

	@Override
	public void validateAccess(Board board, Long memberId, String secretToken) {
		Long tokenBoardId = jwtTokenProvider.getSecretBoardId(secretToken);
		boardService.validateSecretPostAccess(board, memberId, tokenBoardId);
	}
}
