package com.study.backend.board.assembler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.study.backend.board.converter.BoardConverter;
import com.study.backend.board.dto.notice.NoticeList;
import com.study.backend.board.dto.notice.response.NoticeResponse;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.Page;
import com.study.backend.board.service.NoticeService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NoticeResponseAssembler {

	private final NoticeService noticeService;
	private final BoardConverter boardConverter;

	/** 공지사항 목록, 고정 공지 목록, 페이지 정보를 응답 DTO로 조립한다. */
	public NoticeResponse assembleListResponse(List<Board> boardList, Page page) {
		List<NoticeList> noticeList = boardList.stream()
			.map(boardConverter::convertToNoticeList)
			.toList();
		return NoticeResponse.builder()
			.noticeList(noticeList)
			.pinnedNoticeList(noticeService.getPinnedNotices())
			.page(page)
			.build();
	}

	/** 공지사항 상세 조회 결과를 응답 DTO로 변환한다. */
	public Object assembleDetailResponse(Board board) {
		return boardConverter.convertToNoticeDetail(board);
	}
}
