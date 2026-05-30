package com.study.backend.member.service;

import com.study.backend.member.dto.response.LoginResult;
import com.study.backend.member.model.Member;

public interface MemberService {
	LoginResult authenticateMember(Member member);
	void createMember(Member member, String confirmPassword);
	void validateMemberIdAvailable(String memberId);
	void validateIdNotTaken(String memberId);
	void validateIdNotRestricted(String memberId);
	void validatePassword(Member member, String confirmPassword);
}
