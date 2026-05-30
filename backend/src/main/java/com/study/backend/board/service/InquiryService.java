package com.study.backend.board.service;

import java.util.Objects;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.study.backend.board.dto.common.request.BoardUpdateRequest;
import com.study.backend.board.exception.BoardPermissionDeniedException;
import com.study.backend.board.exception.InvalidBoardRequestException;
import com.study.backend.board.mapper.InquiryMapper;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;

@Service
public class InquiryService extends AbstractBoardService<InquiryMapper> {

	private final BCryptPasswordEncoder passwordEncoder;

	public InquiryService(InquiryMapper mapper, BCryptPasswordEncoder passwordEncoder) {
		super(mapper);
		this.passwordEncoder = passwordEncoder;
	}

	/** 비밀번호가 있으면 BCrypt로 암호화한 뒤 문의 게시글을 등록한다. */
	@Transactional
	public void createPost(Board board, Long boardTypeId, Long memberId) {
		board.setSecretPassword(resolveSecretPasswordForCreate(board));
		mapper.createPost(board, boardTypeId, memberId);
	}

	/** 비밀번호가 변경되면 재암호화 후 문의 게시글을 수정한다. 작성자 본인만 가능하다. */
	@Transactional
	public void updatePost(Long boardId, BoardUpdateRequest board, Long memberId) {
		Board updateBoard = mapper.getPostById(boardId);
		validateOwnership(updateBoard, memberId, "수정할 수 있는 권한이 없습니다.");
		if (mapper.isReplied(boardId)) {
			throw new BoardPermissionDeniedException("답변이 완료된 문의는 수정할 수 없습니다.");
		}

		board.setSecretPassword(resolveSecretPasswordForUpdate(updateBoard, board));

		mapper.updatePost(boardId, board, memberId);
	}

	/** 수정 폼에 필요한 게시글을 조회한다. 존재하지 않거나 작성자가 아니면 예외를 던진다. */
	public Board getPostForUpdate(Long boardId, Long memberId) {
		Board board = mapper.getPostForUpdate(boardId);
		validateOwnership(board, memberId, "수정할 수 없습니다.");
		return board;
	}

	/** 문의 게시글에 달린 답변을 조회한다. */
	public Board getInquiryReplyById(Long boardId) {
		return mapper.getInquiryReplyById(boardId);
	}

	/** 입력한 평문 비밀번호가 저장된 해시와 일치하는지 검증한다. */
	public boolean matchesSecretPassword(Long boardId, String rawPassword) {
		String hashedPassword = mapper.getSecretPassword(boardId);
		if (hashedPassword == null) return false;
		return passwordEncoder.matches(rawPassword, hashedPassword);
	}

	/** 로그인 회원이 해당 문의글 작성자인지 확인한다. 게시글이 없으면 예외를 던진다. */
	public boolean isPostOwner(Long boardId, Long memberId) {
		Board board = getPostById(boardId);
		return Objects.equals(board.getMemberId(), memberId);
	}

	/** 비밀글 비밀번호가 일치하지 않으면 예외를 던진다. */
	public void verifySecretPassword(Long boardId, String rawPassword) {
		if (!matchesSecretPassword(boardId, rawPassword)) {
			throw new BoardPermissionDeniedException("비밀번호가 일치하지 않습니다.");
		}
	}

	/** 비밀 문의글 접근 가능 여부를 검증한다. 작성자이거나 검증된 secret token이면 통과한다. */
	public void validateSecretPostAccess(Board board, Long memberId, Long tokenBoardId) {
		if (!Boolean.TRUE.equals(board.getIsSecret())) {
			return;
		}
		if (memberId != null && memberId.equals(board.getMemberId())) {
			return;
		}
		if (!board.getId().equals(tokenBoardId)) {
			throw new BoardPermissionDeniedException("비밀글입니다.");
		}
	}

	/** 문의 게시글을 삭제한다. 작성자 본인만 가능하다. */
	@Transactional
	public void deletePost(Long boardId, Long memberId) {
		Board board = mapper.getPostById(boardId);
		validateOwnership(board, memberId, "삭제할 수 있는 권한이 없습니다.");
		mapper.deletePost(boardId, memberId);
	}

	/** 비밀글이면 비밀번호를 암호화하여 반환하고, 아니면 null을 반환한다. */
	private String resolveSecretPasswordForCreate(Board board) {
		if (!Boolean.TRUE.equals(board.getIsSecret()) || isBlank(board.getSecretPassword())) {
			return null;
		}
		return passwordEncoder.encode(board.getSecretPassword());
	}

	/**
	 * 비밀글 수정 시 비밀번호를 결정한다.
	 * 새 비밀번호 → 기존 비밀번호 → 예외 순으로 우선순위를 갖는다.
	 */
	private String resolveSecretPasswordForUpdate(Board existingBoard, BoardUpdateRequest updateBoard) {
		if (!Boolean.TRUE.equals(updateBoard.getIsSecret())) {
			return null;
		}

		if (!isBlank(updateBoard.getSecretPassword())) {
			return passwordEncoder.encode(updateBoard.getSecretPassword());
		}

		if (!isBlank(existingBoard.getSecretPassword())) {
			return existingBoard.getSecretPassword();
		}

		throw new InvalidBoardRequestException("비공개 시 비밀번호는 숫자 4자리가 필요합니다");
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	@Override
	protected BoardType boardType() {
		return BoardType.INQUIRIES;
	}

}
