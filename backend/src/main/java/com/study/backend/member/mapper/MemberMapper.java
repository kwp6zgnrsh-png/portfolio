package com.study.backend.member.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.study.backend.member.model.Member;

@Mapper
public interface MemberMapper {
	void createMember(Member member);
	Member getMemberById(String memberId);
	boolean isIdBlocked(String memberId);
}
