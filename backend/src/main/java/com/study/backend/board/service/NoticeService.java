package com.study.backend.board.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.study.backend.board.dto.notice.NoticeList;
import com.study.backend.board.mapper.NoticeMapper;
import com.study.backend.board.model.BoardType;

@Service
public class NoticeService extends AbstractBoardService<NoticeMapper> {

	@Value("${notice.pinned-category-id}")
	private Long pinnedCategoryId;

	public NoticeService(NoticeMapper mapper) {
		super(mapper);
	}

	/** 상단 고정 공지 목록을 조회한다. */
	public List<NoticeList> getPinnedNotices() {
		return mapper.getPinnedNotices(pinnedCategoryId, boardType().id());
	}

	@Override
	protected BoardType boardType() {
		return BoardType.NOTICES;
	}
}
