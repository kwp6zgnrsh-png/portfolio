package com.study.backend.board.assembler;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.study.backend.board.converter.BoardConverter;
import com.study.backend.board.dto.inquiry.InquiryDetail;
import com.study.backend.board.dto.inquiry.InquiryList;
import com.study.backend.board.dto.inquiry.InquiryReply;
import com.study.backend.board.dto.inquiry.InquiryUpdate;
import com.study.backend.board.dto.inquiry.response.InquiryDetailResponse;
import com.study.backend.board.dto.inquiry.response.InquiryResponse;
import com.study.backend.board.dto.inquiry.response.InquiryUpdateResponse;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.Page;
import com.study.backend.board.service.InquiryService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class InquiryResponseAssembler {

	private final InquiryService inquiryService;
	private final BoardConverter boardConverter;

	/** 문의 목록을 응답 DTO로 변환하고 페이지 정보를 함께 조립한다. */
	public InquiryResponse assembleListResponse(List<Board> boardList, Page page) {
		List<InquiryList> inquiryList = boardList.stream()
			.map(boardConverter::convertToInquiryList)
			.toList();
		return InquiryResponse.builder()
			.inquiryList(inquiryList)
			.page(page)
			.build();
	}

	/** 문의 상세 응답에 답변 정보와 내 게시글 여부를 함께 조립한다. */
	public InquiryDetailResponse assembleDetailResponse(Board board, Long memberId) {
		InquiryDetail inquiryDetail = boardConverter.convertToInquiryDetail(board);
		InquiryReply inquiryReply = getInquiryReply(board.getId());
		return InquiryDetailResponse.builder()
			.inquiryDetail(inquiryDetail)
			.inquiryReply(inquiryReply)
			.isMyPost(Objects.equals(board.getMemberId(), memberId))
			.build();
	}

	/** 문의 수정 화면에 필요한 게시글 정보를 조립한다. */
	public InquiryUpdateResponse assembleUpdateFormResponse(Board board) {
		InquiryUpdate inquiryUpdate = boardConverter.convertToInquiryUpdate(board);
		return InquiryUpdateResponse.builder()
			.inquiryUpdate(inquiryUpdate)
			.build();
	}

	/** 등록된 답변이 있으면 답변 DTO로 변환하고, 없으면 null을 반환한다. */
	private InquiryReply getInquiryReply(Long boardId) {
		Board findInquiryReply = inquiryService.getInquiryReplyById(boardId);
		return findInquiryReply != null ? boardConverter.convertToInquiryReply(findInquiryReply) : null;
	}
}
