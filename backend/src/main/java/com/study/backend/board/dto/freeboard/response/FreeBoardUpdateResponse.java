package com.study.backend.board.dto.freeboard.response;

import java.util.List;

import com.study.backend.board.dto.freeboard.FreeBoardUpdate;
import com.study.backend.file.model.FileMetaData;

import lombok.Builder;

@Builder
public record FreeBoardUpdateResponse(
	FreeBoardUpdate freeBoardUpdate,
	List<FileMetaData> fileList
) {}
