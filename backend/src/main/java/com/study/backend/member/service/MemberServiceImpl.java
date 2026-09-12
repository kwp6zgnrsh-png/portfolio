package com.study.backend.member.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.study.backend.common.util.JwtTokenProvider;
import com.study.backend.member.dto.response.LoginResult;
import com.study.backend.member.exception.DuplicateMemberIdException;
import com.study.backend.member.exception.LoginFailedException;
import com.study.backend.member.exception.MemberException;
import com.study.backend.member.mapper.MemberMapper;
import com.study.backend.member.model.Member;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

	private final MemberMapper memberMapper;
	private final JwtTokenProvider jwtTokenProvider;
	private final BCryptPasswordEncoder passwordEncoder;

	/** 아이디와 비밀번호를 검증하고 일치하면 토큰과 회원 정보를 반환한다. */
	@Override
	public LoginResult authenticateMember(Member member) {
		Member findMember = memberMapper.getMemberById(member.getMemberId());

		if (findMember == null || !passwordEncoder.matches(member.getMemberPassword(), findMember.getMemberPassword())) {
			throw new LoginFailedException("아이디 혹은 비밀번호가 일치하지 않습니다.");
		}

		String token = jwtTokenProvider.createToken(findMember);
		return new LoginResult(token, findMember.getId(), findMember.getMemberName());
	}

	/** 비밀번호 및 아이디 검증 후 BCrypt로 암호화하여 회원을 저장한다. */
	@Override
	public void createMember(Member member, String confirmPassword) {
		validatePassword(member, confirmPassword);
		validateIdNotRestricted(member.getMemberId());
		validateIdNotTaken(member.getMemberId());
		Member copyMember = member.copyWithPassword(passwordEncoder.encode(member.getMemberPassword()));
		try {
			memberMapper.createMember(copyMember);
		} catch (DuplicateKeyException e) {
			throw new DuplicateMemberIdException("이미 사용 중인 아이디입니다.", e);
		}
	}

	/** 아이디가 사용 금지 목록에 없고, 중복되지 않는지 순서대로 검사한다. */
	@Override
	public void validateMemberIdAvailable(String memberId) {
		validateIdNotRestricted(memberId);
		validateIdNotTaken(memberId);
	}

	/** 이미 사용 중인 아이디이면 예외를 던진다. */
	@Override
	public void validateIdNotTaken(String memberId) {
		Member member = memberMapper.getMemberById(memberId);
		if (member != null) {
			throw new DuplicateMemberIdException("이미 사용 중인 아이디입니다.");
		}
	}

	/** 사용이 금지된 아이디이면 예외를 던진다. */
	@Override
	public void validateIdNotRestricted(String memberId) {
		if(memberMapper.isIdBlocked(memberId)) throw new MemberException("사용할 수 없는 아이디");
	}

	/** 비밀번호 유효성을 검사한다: 확인 비밀번호 일치, 아이디 포함 금지, 연속 문자 3개 이상 금지. */
	@Override
	public void validatePassword(Member member, String confirmPassword) {
		boolean confirmValid = member.getMemberPassword().equals(confirmPassword);
		if(!confirmValid) throw new MemberException("비밀번호와 확인용 비밀번호가 일치하지 않습니다.");

		boolean containsValid = member.getMemberPassword().contains(member.getMemberId());
		if(containsValid) throw new MemberException("비밀번호에 아이디는 포함될 수 없습니다.");

		String password = member.getMemberPassword();
		for (int i = 0; i < password.length() - 2; i++) {
			if (password.charAt(i) == password.charAt(i + 1) && password.charAt(i) == password.charAt(i + 2)) {
				throw new MemberException("비밀번호에 3번 이상 연속된 문자는 허용되지 않습니다.");
			}
		}
	}
}
