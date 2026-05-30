package com.study.backend.board.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Page {
	private Integer totalPages;
	private Integer currentPage;
	private Integer startPage;
	private Integer endPage;
	private Integer prevRange;
	private Integer nextRange;
}
