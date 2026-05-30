package com.study.backend.board.dto.freeboard.response;

import java.util.List;

import com.study.backend.comment.dto.response.CommentResponse;
import com.study.backend.board.dto.freeboard.FreeBoardDetail;
import com.study.backend.file.model.FileMetaData;

import lombok.Builder;

@Builder
public record FreeBoardDetailResponse(
	FreeBoardDetail freeBoardDetail,
	List<CommentResponse> commentList,
	List<FileMetaData> fileList,
	Boolean isMyPost
) {}
