package com.study.backend.board.dto.notice;

import lombok.Builder;

@Builder
public record NoticeList(
	Long id,
	String title,
	Integer views,
	String createdDate,
	String categoryName,
	String author
) {}
