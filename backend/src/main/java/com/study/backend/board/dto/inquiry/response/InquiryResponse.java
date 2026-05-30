package com.study.backend.board.dto.inquiry.response;

import java.util.List;

import com.study.backend.board.dto.inquiry.InquiryList;
import com.study.backend.board.model.Page;

import lombok.Builder;

@Builder
public record InquiryResponse(
	List<InquiryList> inquiryList,
	Page page
) {}
