package com.study.backend.board.dto;

import static org.assertj.core.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.study.backend.board.dto.common.request.BoardUpdateRequest;
import com.study.backend.board.dto.common.request.BoardCreateRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class SecretPostValidationTest {

    private static final String SECRET_PASSWORD_MESSAGE = "비공개 시 비밀번호는 숫자 4자리가 필요합니다";

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    // ── BoardCreateRequest ────────────────────────────────────────────────

	@Test
	@DisplayName("비공개 글 등록 시 비밀게시글 체크는 했지만 비밀번호가 없으면 검증 실패")
	void createRequest_secretWithoutPassword_fails() {
		BoardCreateRequest request = createRequest(true, null);

		Set<ConstraintViolation<BoardCreateRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getMessage().equals(SECRET_PASSWORD_MESSAGE));
    }

	@Test
	@DisplayName("비공개 글 등록 시 비밀번호 4자리 숫자면 검증 통과")
	void createRequest_secretWithValidPassword_passes() {
		BoardCreateRequest request = createRequest(true, "1234");

		Set<ConstraintViolation<BoardCreateRequest>> violations = validator.validate(request);

        assertThat(violations).noneMatch(v -> v.getMessage().equals(SECRET_PASSWORD_MESSAGE));
    }

	@Test
	@DisplayName("비공개 아닌 글 등록 시 비밀번호 없어도 검증 통과")
	void createRequest_publicWithoutPassword_passes() {
		BoardCreateRequest request = createRequest(false, null);

		Set<ConstraintViolation<BoardCreateRequest>> violations = validator.validate(request);

        assertThat(violations).noneMatch(v -> v.getMessage().equals(SECRET_PASSWORD_MESSAGE));
    }

	@Test
	@DisplayName("비밀번호가 4자리 숫자가 아니면 검증 실패")
	void createRequest_invalidPasswordFormat_fails() {
		BoardCreateRequest request = createRequest(true, "abcd");

		Set<ConstraintViolation<BoardCreateRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getMessage().equals(SECRET_PASSWORD_MESSAGE));
    }

    // ── BoardUpdate ───────────────────────────────────────────────────────

    @Test
    @DisplayName("비공개 글 수정 시 비밀번호가 없어도 기존 비밀번호 유지 용도로 검증 통과")
    void boardUpdate_secretWithoutPassword_passes() {
        BoardUpdateRequest update = BoardUpdateRequest.builder()
            .title("제목").content("내용")
            .isSecret(true).secretPassword(null)
            .build();

        Set<ConstraintViolation<BoardUpdateRequest>> violations = validator.validate(update);

        assertThat(violations).noneMatch(v -> v.getMessage().equals(SECRET_PASSWORD_MESSAGE));
    }

    @Test
    @DisplayName("비공개 글 수정 시 비밀번호 4자리 숫자면 검증 통과")
    void boardUpdate_secretWithValidPassword_passes() {
        BoardUpdateRequest update = BoardUpdateRequest.builder()
            .title("제목").content("내용")
            .isSecret(true).secretPassword("5678")
            .build();

        Set<ConstraintViolation<BoardUpdateRequest>> violations = validator.validate(update);

        assertThat(violations).noneMatch(v -> v.getMessage().equals(SECRET_PASSWORD_MESSAGE));
    }

    @Test
    @DisplayName("비공개 아닌 글 수정 시 비밀번호 없어도 검증 통과")
    void boardUpdate_publicWithoutPassword_passes() {
        BoardUpdateRequest update = BoardUpdateRequest.builder()
            .title("제목").content("내용")
            .isSecret(false).secretPassword(null)
            .build();

        Set<ConstraintViolation<BoardUpdateRequest>> violations = validator.validate(update);

        assertThat(violations).noneMatch(v -> v.getMessage().equals(SECRET_PASSWORD_MESSAGE));
    }

    @Test
    @DisplayName("비공개 글 수정 시 비밀번호 형식이 틀리면 검증 실패")
    void boardUpdate_secretWithInvalidPassword_fails() {
        BoardUpdateRequest update = BoardUpdateRequest.builder()
            .title("제목").content("내용")
            .isSecret(true).secretPassword("abcd")
            .build();

        Set<ConstraintViolation<BoardUpdateRequest>> violations = validator.validate(update);

		assertThat(violations).anyMatch(v -> v.getMessage().equals(SECRET_PASSWORD_MESSAGE));
	}

	private BoardCreateRequest createRequest(Boolean isSecret, String secretPassword) {
		return new BoardCreateRequest("제목", "내용", null, isSecret, secretPassword);
	}
}
