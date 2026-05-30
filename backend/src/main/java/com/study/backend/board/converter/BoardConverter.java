package com.study.backend.board.converter;

import java.util.Set;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.study.backend.board.dto.common.request.BoardCreateRequest;
import com.study.backend.board.dto.common.request.SearchRequest;
import com.study.backend.board.dto.freeboard.FreeBoardDetail;
import com.study.backend.board.dto.freeboard.FreeBoardList;
import com.study.backend.board.dto.freeboard.FreeBoardUpdate;
import com.study.backend.board.dto.gallery.GalleryDetail;
import com.study.backend.board.dto.gallery.GalleryList;
import com.study.backend.board.dto.gallery.GalleryUpdate;
import com.study.backend.board.dto.home.HomeFreeBoard;
import com.study.backend.board.dto.home.HomeGallery;
import com.study.backend.board.dto.home.HomeInquiry;
import com.study.backend.board.dto.home.HomeNotice;
import com.study.backend.board.dto.inquiry.InquiryDetail;
import com.study.backend.board.dto.inquiry.InquiryList;
import com.study.backend.board.dto.inquiry.InquiryReply;
import com.study.backend.board.dto.inquiry.InquiryUpdate;
import com.study.backend.board.dto.notice.NoticeDetail;
import com.study.backend.board.dto.notice.NoticeList;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.Search;

@Mapper(componentModel = "spring")
public interface BoardConverter {

	NoticeList convertToNoticeList(Board board);

	@Mapping(target = "fileCount", source = "fileCount", defaultValue = "0")
	@Mapping(target = "commentCount", source = "commentCount", defaultValue = "0")
	FreeBoardList convertToFreeBoardList(Board board);

	@Mapping(target = "fileCount", source = "fileCount", defaultValue = "0")
	HomeFreeBoard convertToHomeFreeBoard(Board board);

	GalleryList convertToGalleryList(Board board);

	InquiryList convertToInquiryList(Board board);

	NoticeDetail convertToNoticeDetail(Board board);

	FreeBoardDetail convertToFreeBoardDetail(Board board);

	GalleryDetail convertToGalleryDetail(Board board);

	InquiryUpdate convertToInquiryUpdate(Board board);

	InquiryDetail convertToInquiryDetail(Board board);

	InquiryReply convertToInquiryReply(Board board);

	FreeBoardUpdate convertToFreeBoardUpdate(Board board);

	GalleryUpdate convertToGalleryUpdate(Board board);

	HomeGallery convertToHomeGallery(Board board);

	HomeInquiry convertToHomeInquiry(Board board);

	HomeNotice convertToHomeNotice(Board board);

	Search convertToSearch(SearchRequest searchRequest);

	Board convertToBoard(BoardCreateRequest createRequest);

	/** SQL injection 방지: orderBy/direction에 허용된 값만 통과시킨다. */
	@AfterMapping
	default void sanitizeSortParameters(@MappingTarget Search search) {

		Set<String> allowedFields = Set.of("title", "views", "categoryName");

		if (search.getOrderByField() == null || !allowedFields.contains(search.getOrderByField())) {
			search.setOrderByField("id");
		}
		if (!"ASC".equalsIgnoreCase(search.getDirection()) && !"DESC".equalsIgnoreCase(search.getDirection())) {
			search.setDirection("DESC");
		} else {
			search.setDirection(search.getDirection().toUpperCase());
		}
	}
}
