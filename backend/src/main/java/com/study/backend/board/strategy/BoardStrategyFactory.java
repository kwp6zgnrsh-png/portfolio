package com.study.backend.board.strategy;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.study.backend.board.exception.BoardTypeException;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;

@Component
public class BoardStrategyFactory {

	private static final Map<BoardType, String> STRATEGY_BEAN_NAMES = new EnumMap<>(BoardType.class);

	static {
		STRATEGY_BEAN_NAMES.put(BoardType.NOTICES, "noticesStrategy");
		STRATEGY_BEAN_NAMES.put(BoardType.BOARDS, "boardsStrategy");
		STRATEGY_BEAN_NAMES.put(BoardType.GALLERIES, "galleriesStrategy");
		STRATEGY_BEAN_NAMES.put(BoardType.INQUIRIES, "inquiriesStrategy");
	}

	private final Map<String, BoardReadableStrategy> strategies;

	public BoardStrategyFactory(Map<String, BoardReadableStrategy> strategies) {
		this.strategies = strategies;
	}

	/** 게시판 타입 문자열로 조회 가능한 게시판 전략을 반환한다. */
	public BoardReadableStrategy requireReadableStrategy(String boardType) {
		return resolveStrategy(boardType);
	}

	/** 생성을 지원하는 게시판 전략을 반환한다. 해당 게시판이 생성을 지원하지 않으면 예외를 던진다. */
	public BoardCreateStrategy requireCreateStrategy(String boardType) {
		BoardType type = BoardType.from(boardType);
		if (!type.supportsCreate()) {
			throw new BoardTypeException(boardType + " 게시판은 쓰기를 지원하지 않습니다");
		}
		BoardReadableStrategy strategy = resolveStrategy(boardType);
		if (!(strategy instanceof BoardCreateStrategy creatable)) {
			throw new BoardTypeException(boardType + " 게시판은 쓰기를 지원하지 않습니다");
		}
		return creatable;
	}

	/** 수정을 지원하는 게시판 전략을 반환한다. 해당 게시판이 수정을 지원하지 않으면 예외를 던진다. */
	public BoardUpdateStrategy requireUpdateStrategy(String boardType) {
		BoardType type = BoardType.from(boardType);
		if (!type.supportsUpdate()) {
			throw new BoardTypeException(boardType + " 게시판은 수정을 지원하지 않습니다");
		}
		BoardReadableStrategy strategy = resolveStrategy(boardType);
		if (!(strategy instanceof BoardUpdateStrategy updatable)) {
			throw new BoardTypeException(boardType + " 게시판은 수정을 지원하지 않습니다");
		}
		return updatable;
	}

	/** 삭제를 지원하는 게시판 전략을 반환한다. 해당 게시판이 삭제를 지원하지 않으면 예외를 던진다. */
	public BoardDeleteStrategy requireDeleteStrategy(String boardType) {
		BoardType type = BoardType.from(boardType);
		if (!type.supportsDelete()) {
			throw new BoardTypeException(boardType + " 게시판은 삭제를 지원하지 않습니다");
		}
		BoardReadableStrategy strategy = resolveStrategy(boardType);
		if (!(strategy instanceof BoardDeleteStrategy deletable)) {
			throw new BoardTypeException(boardType + " 게시판은 삭제를 지원하지 않습니다");
		}
		return deletable;
	}

	/** 비밀글을 지원하는 게시판 전략을 반환한다. 해당 게시판이 비밀글을 지원하지 않으면 예외를 던진다. */
	public BoardSecretStrategy requireSecretStrategy(String boardType) {
		BoardType type = BoardType.from(boardType);
		if (!type.supportsSecretPost()) {
			throw new BoardTypeException(boardType + " 게시판은 비밀글을 지원하지 않습니다");
		}
		BoardReadableStrategy strategy = resolveStrategy(boardType);
		if (!(strategy instanceof BoardSecretStrategy secret)) {
			throw new BoardTypeException(boardType + " 게시판은 비밀글을 지원하지 않습니다");
		}
		return secret;
	}

	/** 게시판 타입에 따라 필요한 접근 검증을 수행한다. 비밀글 정책이 없는 게시판은 통과시킨다. */
	public void validateAccess(String boardType, Board board, Long memberId, String secretToken) {
		BoardType type = BoardType.from(boardType);
		if (!type.supportsSecretPost()) {
			return;
		}
		requireSecretStrategy(boardType).validateAccess(board, memberId, secretToken);
	}

	/** 게시판 타입 문자열을 BoardType enum으로 변환한 뒤 등록된 전략 빈을 조회한다. */
	private BoardReadableStrategy resolveStrategy(String boardType) {
		BoardType type = BoardType.from(boardType);
		String strategyName = STRATEGY_BEAN_NAMES.get(type);
		BoardReadableStrategy strategy = strategies.get(strategyName);
		if (strategy == null) {
			throw new BoardTypeException("지원하지 않는 게시판입니다: " + boardType);
		}
		return strategy;
	}
}
