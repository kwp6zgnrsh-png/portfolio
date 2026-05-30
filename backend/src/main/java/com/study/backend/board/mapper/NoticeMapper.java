package com.study.backend.board.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.study.backend.board.dto.notice.NoticeList;

@Mapper
public interface NoticeMapper extends BoardMapper{
	List<NoticeList> getPinnedNotices(Long pinnedCategoryId, Long boardTypeId);
}
