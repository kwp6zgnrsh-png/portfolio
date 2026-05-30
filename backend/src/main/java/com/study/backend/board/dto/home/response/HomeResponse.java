package com.study.backend.board.dto.home.response;

import java.util.List;

import com.study.backend.board.dto.home.HomeFreeBoard;
import com.study.backend.board.dto.home.HomeGallery;
import com.study.backend.board.dto.home.HomeInquiry;
import com.study.backend.board.dto.home.HomeNotice;

import lombok.Builder;

@Builder
public record HomeResponse(
	List<HomeNotice> noticeList,
	List<HomeFreeBoard> freeBoardList,
	List<HomeGallery> galleryList,
	List<HomeInquiry> inquiryList
) {}
