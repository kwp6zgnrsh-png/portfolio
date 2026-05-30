package com.study.backend.member.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Member {
	private Long id;
	private String memberId;
	private String memberPassword;
	private String memberName;

	public Member copyWithPassword(String password){
		return Member.builder()
			.memberId(memberId)
			.memberPassword(password)
			.memberName(memberName)
			.build();
	}
}
