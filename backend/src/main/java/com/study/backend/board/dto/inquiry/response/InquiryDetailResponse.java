package com.study.backend.board.dto.inquiry.response;

import com.study.backend.board.dto.inquiry.InquiryReply;
import com.study.backend.board.dto.inquiry.InquiryDetail;

import lombok.Builder;

@Builder
public record InquiryDetailResponse(
	InquiryDetail inquiryDetail,
	InquiryReply inquiryReply,
	Boolean isMyPost
) {}
