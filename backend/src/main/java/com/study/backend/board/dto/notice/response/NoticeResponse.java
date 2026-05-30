package com.study.backend.board.dto.notice.response;

import java.util.List;

import com.study.backend.board.dto.notice.NoticeList;
import com.study.backend.board.model.Page;

import lombok.Builder;

@Builder
public record NoticeResponse(
	List<NoticeList> noticeList,
	List<NoticeList> pinnedNoticeList,
	Page page
) {}
