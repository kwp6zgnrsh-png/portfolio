package com.study.backend.board.dto.notice;

import lombok.Builder;

@Builder
public record NoticeDetail(
	Long id,
	String categoryName,
	String content,
	String createdDate,
	Integer views,
	String title,
	String author
) {}
