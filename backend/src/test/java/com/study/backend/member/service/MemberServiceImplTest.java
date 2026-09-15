package com.study.backend.member.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.study.backend.common.util.JwtTokenProvider;
import com.study.backend.member.dto.response.LoginResult;
import com.study.backend.member.exception.DuplicateMemberIdException;
import com.study.backend.member.exception.LoginFailedException;
import com.study.backend.member.exception.MemberException;
import com.study.backend.member.mapper.MemberMapper;
import com.study.backend.member.model.Member;

@ExtendWith(MockitoExtension.class)
class MemberServiceImplTest {

    @Mock MemberMapper memberMapper;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock BCryptPasswordEncoder passwordEncoder;

    @InjectMocks MemberServiceImpl memberService;

    // ── authenticateMember ──────────────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 아이디로 로그인하면 예외를 던진다")
    void authenticateMember_memberNotFound_throws() {
        given(memberMapper.getMemberById("unknown")).willReturn(null);

        assertThatThrownBy(() -> memberService.authenticateMember(member("unknown", "pass")))
            .isInstanceOf(LoginFailedException.class)
            .hasMessageContaining("아이디 혹은 비밀번호가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 예외를 던진다")
    void authenticateMember_wrongPassword_throws() {
        Member stored = member("user1", "encodedPass");
        given(memberMapper.getMemberById("user1")).willReturn(stored);
        given(passwordEncoder.matches("wrongPass", "encodedPass")).willReturn(false);

        assertThatThrownBy(() -> memberService.authenticateMember(member("user1", "wrongPass")))
            .isInstanceOf(LoginFailedException.class)
            .hasMessageContaining("아이디 혹은 비밀번호가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("아이디와 비밀번호가 일치하면 JWT 토큰을 반환한다")
    void authenticateMember_correctCredentials_returnsToken() {
        Member stored = member("user1", "encodedPass");
        given(memberMapper.getMemberById("user1")).willReturn(stored);
        given(passwordEncoder.matches("rawPass", "encodedPass")).willReturn(true);
        given(jwtTokenProvider.createToken(stored)).willReturn("jwt-token");

        LoginResult result = memberService.authenticateMember(member("user1", "rawPass"));

        assertThat(result.token()).isEqualTo("jwt-token");
    }

    // ── validatePassword ────────────────────────────────────────────────

    @Test
    @DisplayName("확인 비밀번호가 다르면 예외를 던진다")
    void validatePassword_confirmMismatch_throws() {
        Member m = member("user1", "password123");

        assertThatThrownBy(() -> memberService.validatePassword(m, "different123"))
            .isInstanceOf(MemberException.class)
            .hasMessageContaining("비밀번호와 확인용 비밀번호가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("비밀번호에 아이디가 포함되면 예외를 던진다")
    void validatePassword_containsMemberId_throws() {
        Member m = member("user1", "user1password");

        assertThatThrownBy(() -> memberService.validatePassword(m, "user1password"))
            .isInstanceOf(MemberException.class)
            .hasMessageContaining("아이디는 포함될 수 없습니다.");
    }

    @Test
    @DisplayName("비밀번호에 동일 문자가 3회 이상 연속되면 예외를 던진다")
    void validatePassword_tripleConsecutiveChars_throws() {
        Member m = member("user1", "aaa12345");

        assertThatThrownBy(() -> memberService.validatePassword(m, "aaa12345"))
            .isInstanceOf(MemberException.class)
            .hasMessageContaining("3번 이상 연속된 문자");
    }

    @Test
    @DisplayName("유효한 비밀번호는 예외 없이 통과한다")
    void validatePassword_validPassword_passes() {
        Member m = member("user1", "ab12cd34");

        assertThatNoException().isThrownBy(() -> memberService.validatePassword(m, "ab12cd34"));
    }

    // ── validateIdNotTaken ──────────────────────────────────────────────

    @Test
    @DisplayName("이미 사용 중인 아이디이면 예외를 던진다")
    void validateIdNotTaken_duplicateId_throws() {
        given(memberMapper.getMemberById("taken")).willReturn(member("taken", "pass"));

        assertThatThrownBy(() -> memberService.validateIdNotTaken("taken"))
            .isInstanceOf(DuplicateMemberIdException.class);
    }

    @Test
    @DisplayName("사용 가능한 아이디이면 예외 없이 통과한다")
    void validateIdNotTaken_availableId_passes() {
        given(memberMapper.getMemberById("newId")).willReturn(null);

        assertThatNoException().isThrownBy(() -> memberService.validateIdNotTaken("newId"));
    }

    @Test
    @DisplayName("아이디 사용 가능 여부 검사는 금지 목록 확인 후 중복 검사를 수행한다")
    void validateMemberIdAvailable_checksRestrictedThenTaken() {
        given(memberMapper.isIdBlocked("newId")).willReturn(false);
        given(memberMapper.getMemberById("newId")).willReturn(null);

        assertThatNoException().isThrownBy(() -> memberService.validateMemberIdAvailable("newId"));

        InOrder inOrder = inOrder(memberMapper);
        inOrder.verify(memberMapper).isIdBlocked("newId");
        inOrder.verify(memberMapper).getMemberById("newId");
    }

    @Test
    @DisplayName("회원 저장 시 DB 중복 예외가 나면 DuplicateMemberIdException으로 변환한다")
    void createMember_duplicateAtInsert_throwsDuplicateMemberIdException() {
        Member member = member("newId", "ab12cd34");
        given(memberMapper.getMemberById("newId")).willReturn(null);
        given(memberMapper.isIdBlocked("newId")).willReturn(false);
        given(passwordEncoder.encode("ab12cd34")).willReturn("encoded");
        willThrow(new DuplicateKeyException("duplicate"))
            .given(memberMapper)
            .createMember(any(Member.class));

        assertThatThrownBy(() -> memberService.createMember(member, "ab12cd34"))
            .isInstanceOf(DuplicateMemberIdException.class)
            .hasMessageContaining("이미 사용 중인 아이디입니다.");
    }

    // ── validateIdNotRestricted ─────────────────────────────────────────

    @Test
    @DisplayName("금지된 아이디이면 예외를 던진다")
    void validateIdNotRestricted_blockedId_throws() {
        given(memberMapper.isIdBlocked("admin")).willReturn(true);

        assertThatThrownBy(() -> memberService.validateIdNotRestricted("admin"))
            .isInstanceOf(MemberException.class);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private Member member(String id, String password) {
        return Member.builder().memberId(id).memberPassword(password).build();
    }
}
