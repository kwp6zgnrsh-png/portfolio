package com.study.backend.board.assembler;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.study.backend.board.converter.BoardConverter;
import com.study.backend.board.dto.gallery.GalleryDetail;
import com.study.backend.board.dto.gallery.GalleryList;
import com.study.backend.board.dto.gallery.GalleryUpdate;
import com.study.backend.board.dto.gallery.response.GalleryDetailResponse;
import com.study.backend.board.dto.gallery.response.GalleryResponse;
import com.study.backend.board.dto.gallery.response.GalleryUpdateResponse;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;
import com.study.backend.board.model.Page;
import com.study.backend.file.model.FileMetaData;
import com.study.backend.file.service.FileServiceFactory;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GalleryResponseAssembler {

	private final BoardConverter boardConverter;
	private final FileServiceFactory fileService;

	/** 갤러리 목록을 응답 DTO로 변환하고 페이지 정보를 함께 조립한다. */
	public GalleryResponse assembleListResponse(List<Board> boardList, Page page) {
		List<GalleryList> galleryList = boardList.stream()
			.map(boardConverter::convertToGalleryList)
			.toList();
		return GalleryResponse.builder()
			.galleryList(galleryList)
			.page(page)
			.build();
	}

	/** 갤러리 상세 응답에 이미지 목록과 내 게시글 여부를 함께 조립한다. */
	public GalleryDetailResponse assembleDetailResponse(Board board, Long memberId) {
		GalleryDetail galleryDetail = boardConverter.convertToGalleryDetail(board);
		return GalleryDetailResponse.builder()
			.galleryDetail(galleryDetail)
			.galleryImageList(getFilesByBoardId(board.getId()))
			.isMyPost(Objects.equals(board.getMemberId(), memberId))
			.build();
	}

	/** 갤러리 수정 화면에 필요한 게시글 정보와 이미지 파일을 조립한다. */
	public GalleryUpdateResponse assembleUpdateFormResponse(Board board) {
		GalleryUpdate galleryUpdate = boardConverter.convertToGalleryUpdate(board);
		return GalleryUpdateResponse.builder()
			.galleryUpdate(galleryUpdate)
			.fileList(getFilesByBoardId(board.getId()))
			.build();
	}

	private List<FileMetaData> getFilesByBoardId(Long boardId) {
		return fileService.getFileService(BoardType.GALLERIES).getFilesByBoardId(boardId);
	}
}
