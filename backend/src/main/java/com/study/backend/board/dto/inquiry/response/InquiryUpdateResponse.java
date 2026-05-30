package com.study.backend.board.dto.inquiry.response;

import com.study.backend.board.dto.inquiry.InquiryUpdate;

import lombok.Builder;

@Builder
public record InquiryUpdateResponse(
	InquiryUpdate inquiryUpdate
) {}
