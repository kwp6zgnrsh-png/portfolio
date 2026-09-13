package com.study.backend.board.assembler;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Component;

import com.study.backend.board.converter.BoardConverter;
import com.study.backend.board.dto.freeboard.FreeBoardDetail;
import com.study.backend.board.dto.freeboard.FreeBoardList;
import com.study.backend.board.dto.freeboard.FreeBoardUpdate;
import com.study.backend.board.dto.freeboard.response.FreeBoardDetailResponse;
import com.study.backend.board.dto.freeboard.response.FreeBoardResponse;
import com.study.backend.board.dto.freeboard.response.FreeBoardUpdateResponse;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;
import com.study.backend.board.model.Page;
import com.study.backend.comment.dto.response.CommentResponse;
import com.study.backend.comment.model.Comment;
import com.study.backend.comment.service.CommentService;
import com.study.backend.common.util.BoardQueryRunner;
import com.study.backend.file.model.FileMetaData;
import com.study.backend.file.service.FileServiceFactory;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FreeBoardResponseAssembler {

	private final CommentService commentService;
	private final BoardConverter boardConverter;
	private final FileServiceFactory fileService;
	private final BoardQueryRunner boardQueryRunner;

	/** 자유게시판 목록을 응답 DTO로 변환하고 페이지 정보를 함께 조립한다. */
	public FreeBoardResponse assembleListResponse(List<Board> boardList, Page page) {
		List<FreeBoardList> freeBoardList = boardList.stream()
			.map(boardConverter::convertToFreeBoardList)
			.toList();
		return FreeBoardResponse.builder()
			.freeBoardList(freeBoardList)
			.page(page)
			.build();
	}

	/** 자유게시판 상세 응답에 댓글, 첨부 파일, 내 게시글 여부를 함께 조립한다. */
	public FreeBoardDetailResponse assembleDetailResponse(Board board, Long memberId) {
		FreeBoardDetail freeBoardDetail = boardConverter.convertToFreeBoardDetail(board);

		CompletableFuture<List<Comment>> commentsFuture = boardQueryRunner.submit(() -> getCommentByBoardId(board.getId()));

		CompletableFuture<List<FileMetaData>> filesFuture = boardQueryRunner.submit(() -> getFilesByBoardId(board.getId()));

		boardQueryRunner.awaitAll(commentsFuture, filesFuture);

		return FreeBoardDetailResponse.builder()
			.freeBoardDetail(freeBoardDetail)
			.commentList(markMyComments(commentsFuture.join(), memberId))
			.fileList(filesFuture.join())
			.isMyPost(Objects.equals(freeBoardDetail.memberId(), memberId))
			.build();
	}

	/** 자유게시판 수정 화면에 필요한 게시글 정보와 첨부 파일을 조립한다. */
	public FreeBoardUpdateResponse assembleUpdateFormResponse(Board board) {
		FreeBoardUpdate freeBoardUpdate = boardConverter.convertToFreeBoardUpdate(board);
		return FreeBoardUpdateResponse.builder()
			.freeBoardUpdate(freeBoardUpdate)
			.fileList(getFilesByBoardId(board.getId()))
			.build();
	}

	/** 현재 로그인 회원 기준으로 각 댓글의 내 댓글 여부를 표시한다. */
	private List<CommentResponse> markMyComments(List<Comment> findComments, Long memberId) {
		return findComments.stream()
			.map(c -> CommentResponse.builder()
				.id(c.getId())
				.author(c.getAuthor())
				.content(c.getContent())
				.createdDate(c.getCreatedDate())
				.isMyComment(Objects.equals(memberId, c.getMemberId()))
				.build())
			.toList();
	}

	private List<Comment> getCommentByBoardId(Long boardId) {
		return commentService.getCommentsByBoardId(boardId);
	}

	private List<FileMetaData> getFilesByBoardId(Long boardId) {
		return fileService.getFileService(BoardType.BOARDS).getFilesByBoardId(boardId);
	}
}
