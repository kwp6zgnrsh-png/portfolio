package com.study.backend.common.util;

import org.springframework.stereotype.Component;

import com.study.backend.board.model.Page;

@Component
public class Pagination {

	/* 한 번에 표시할 페이지 수 */
	private static final int PAGES_TO_DISPLAY = 10;

	public Page pagination(Integer postCount, Integer page, Integer limit){
		int safeLimit = (limit == null || limit <= 0) ? 10 : limit;
		int safePostCount = (postCount == null || postCount < 0) ? 0 : postCount;
		int totalPages = safePostCount == 0 ? 1 : (int) (((long) safePostCount + safeLimit - 1) / safeLimit);
		int currentPage = (page == null || page <= 0) ? 1 : Math.min(page, totalPages);
		int startPage = ((currentPage - 1) / PAGES_TO_DISPLAY) * PAGES_TO_DISPLAY + 1;
		int endPage = Math.min(startPage + PAGES_TO_DISPLAY - 1, totalPages);
		int prevRange = startPage - PAGES_TO_DISPLAY;
		int nextRange = endPage + 1;

		return Page.builder()
			.totalPages(totalPages)
			.currentPage(currentPage)
			.startPage(startPage)
			.endPage(endPage)
			.prevRange(prevRange)
			.nextRange(nextRange)
			.build();
	}
}