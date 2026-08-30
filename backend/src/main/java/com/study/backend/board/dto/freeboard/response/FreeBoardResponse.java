package com.study.backend.board.dto.freeboard.response;

import java.util.List;

import com.study.backend.board.dto.freeboard.FreeBoardList;
import com.study.backend.board.model.Page;

import lombok.Builder;

@Builder
public record FreeBoardResponse(
	List<FreeBoardList> freeBoardList,
	Page page
) {}
