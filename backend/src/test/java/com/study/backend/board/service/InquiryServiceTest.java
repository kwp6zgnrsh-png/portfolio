package com.study.backend.board.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.study.backend.board.dto.common.request.BoardUpdateRequest;
import com.study.backend.board.exception.InvalidBoardRequestException;
import com.study.backend.board.exception.BoardNotFoundException;
import com.study.backend.board.exception.BoardPermissionDeniedException;
import com.study.backend.board.mapper.InquiryMapper;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;

@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    @Mock InquiryMapper inquiryMapper;
    @Mock BCryptPasswordEncoder passwordEncoder;

    @InjectMocks InquiryService inquiryService;

    // ── createPost ──────────────────────────────────────────────────────

    @Test
    @DisplayName("비밀글 등록 시 비밀번호가 BCrypt로 암호화된다")
    void createPost_secretPost_passwordEncoded() {
        Board board = Board.builder().isSecret(true).secretPassword("1234").build();
        given(passwordEncoder.encode("1234")).willReturn("encoded");

        inquiryService.createPost(board, 4L, 1L);

        assertThat(board.getSecretPassword()).isEqualTo("encoded");
    }

    @Test
    @DisplayName("비밀글이 아니면 비밀번호가 null로 설정된다")
    void createPost_notSecret_passwordNull() {
        Board board = Board.builder().isSecret(false).secretPassword("1234").build();

        inquiryService.createPost(board, 4L, 1L);

        assertThat(board.getSecretPassword()).isNull();
    }

    @Test
    @DisplayName("비밀글이지만 비밀번호가 null이면 비밀번호가 null로 설정된다")
    void createPost_secretButNullPassword_passwordNull() {
        Board board = Board.builder().isSecret(true).secretPassword(null).build();

        inquiryService.createPost(board, 4L, 1L);

        assertThat(board.getSecretPassword()).isNull();
    }

    // ── updatePost ──────────────────────────────────────────────────────

    @Test
    @DisplayName("게시글이 존재하지 않으면 수정 시 예외를 던진다")
    void updatePost_boardNotFound_throws() {
        BoardUpdateRequest update = boardUpdate(null);
        given(inquiryMapper.getPostById(1L)).willReturn(null);

        assertThatThrownBy(() -> inquiryService.updatePost(1L, update, 1L))
            .isInstanceOf(BoardNotFoundException.class);
    }

    @Test
    @DisplayName("작성자가 아니면 수정 시 예외를 던진다")
    void updatePost_notOwner_throws() {
        BoardUpdateRequest update = boardUpdate(null);
        given(inquiryMapper.getPostById(1L)).willReturn(board(2L));

        assertThatThrownBy(() -> inquiryService.updatePost(1L, update, 1L))
            .isInstanceOf(BoardPermissionDeniedException.class);
    }

    @Test
    @DisplayName("수정 시 새 비밀번호가 있으면 BCrypt로 암호화된다")
    void updatePost_newPassword_encoded() {
        BoardUpdateRequest update = BoardUpdateRequest.builder()
            .title("제목").content("내용")
            .isSecret(true).secretPassword("5678")
            .build();
        given(inquiryMapper.getPostById(1L)).willReturn(board(1L));
        given(inquiryMapper.isReplied(1L)).willReturn(false);
        given(passwordEncoder.encode("5678")).willReturn("encoded");

        inquiryService.updatePost(1L, update, 1L);

        assertThat(update.getSecretPassword()).isEqualTo("encoded");
    }

    @Test
	@DisplayName("공개 여부가 누락되면 일반글로 정규화하고 비밀번호를 제거한다")
	void updatePost_isSecretMissing_normalizesToPublic() {
		BoardUpdateRequest update = boardUpdate(null);
		given(inquiryMapper.getPostById(1L)).willReturn(board(1L));
		given(inquiryMapper.isReplied(1L)).willReturn(false);

		inquiryService.updatePost(1L, update, 1L);

		assertThat(update.getIsSecret()).isFalse();
		assertThat(update.getSecretPassword()).isNull();
	}

    @Test
    @DisplayName("기존 비밀글 수정 시 새 비밀번호가 없으면 기존 비밀번호 해시를 유지한다")
    void updatePost_secretWithoutNewPassword_keepsExistingHash() {
        BoardUpdateRequest update = BoardUpdateRequest.builder()
            .title("제목").content("내용")
            .isSecret(true).secretPassword(null)
            .build();
        given(inquiryMapper.getPostById(1L)).willReturn(secretBoard(1L, "stored-hash"));
        given(inquiryMapper.isReplied(1L)).willReturn(false);

        inquiryService.updatePost(1L, update, 1L);

        assertThat(update.getSecretPassword()).isEqualTo("stored-hash");
    }

    @Test
    @DisplayName("공개글을 비밀글로 바꾸면서 비밀번호가 없으면 예외를 던진다")
    void updatePost_publicToSecretWithoutPassword_throws() {
        BoardUpdateRequest update = BoardUpdateRequest.builder()
            .title("제목").content("내용")
            .isSecret(true).secretPassword(null)
            .build();
        given(inquiryMapper.getPostById(1L)).willReturn(board(1L));
        given(inquiryMapper.isReplied(1L)).willReturn(false);

        assertThatThrownBy(() -> inquiryService.updatePost(1L, update, 1L))
            .isInstanceOf(InvalidBoardRequestException.class)
            .hasMessageContaining("비공개 시 비밀번호는 숫자 4자리가 필요합니다");
    }

    @Test
    @DisplayName("답변이 완료된 문의글이면 수정 시 예외를 던진다")
    void updatePost_alreadyReplied_throws() {
        given(inquiryMapper.getPostById(1L)).willReturn(board(1L));
        given(inquiryMapper.isReplied(1L)).willReturn(true);

        assertThatThrownBy(() -> inquiryService.updatePost(1L, boardUpdate(null), 1L))
            .isInstanceOf(BoardPermissionDeniedException.class)
            .hasMessageContaining("답변이 완료된 문의는 수정할 수 없습니다.");
    }

    @Test
    @DisplayName("답변이 없는 문의글이면 수정이 정상 처리된다")
    void updatePost_notReplied_success() {
        given(inquiryMapper.getPostById(1L)).willReturn(board(1L));
        given(inquiryMapper.isReplied(1L)).willReturn(false);

        assertThatNoException().isThrownBy(() -> inquiryService.updatePost(1L, boardUpdate(null), 1L));
    }

    // ── matchesSecretPassword ───────────────────────────────────────────

    @Test
    @DisplayName("비밀번호가 일치하면 true를 반환한다")
    void matchesSecretPassword_correct_returnsTrue() {
        given(inquiryMapper.getSecretPassword(1L)).willReturn("encoded");
        given(passwordEncoder.matches("1234", "encoded")).willReturn(true);

        assertThat(inquiryService.matchesSecretPassword(1L, "1234")).isTrue();
    }

    @Test
    @DisplayName("저장된 비밀번호가 없으면 false를 반환한다")
    void matchesSecretPassword_noPassword_returnsFalse() {
        given(inquiryMapper.getSecretPassword(1L)).willReturn(null);

        assertThat(inquiryService.matchesSecretPassword(1L, "1234")).isFalse();
    }

    // ── deletePost ──────────────────────────────────────────────────────

    @Test
    @DisplayName("게시글이 존재하지 않으면 삭제 시 예외를 던진다")
    void deletePost_boardNotFound_throws() {
        given(inquiryMapper.getPostById(1L)).willReturn(null);

        assertThatThrownBy(() -> inquiryService.deletePost(1L, 1L))
            .isInstanceOf(BoardNotFoundException.class);
    }

    @Test
    @DisplayName("작성자가 아니면 삭제 시 예외를 던진다")
    void deletePost_notOwner_throws() {
        given(inquiryMapper.getPostById(1L)).willReturn(board(2L));

        assertThatThrownBy(() -> inquiryService.deletePost(1L, 1L))
            .isInstanceOf(BoardPermissionDeniedException.class);
    }

    @Test
    @DisplayName("작성자 본인이면 삭제가 정상 처리된다")
    void deletePost_owner_success() {
        given(inquiryMapper.getPostById(1L)).willReturn(board(1L));

        assertThatNoException().isThrownBy(() -> inquiryService.deletePost(1L, 1L));
        then(inquiryMapper).should().deletePost(1L, 1L);
    }

    // ── helpers ─────────────────────────────────────────────────────────

	private Board board(Long memberId) {
		return Board.builder().id(1L).memberId(memberId).boardTypeId(BoardType.INQUIRIES.id()).build();
	}

    private Board secretBoard(Long memberId, String secretPassword) {
        return Board.builder()
			.id(1L)
			.memberId(memberId)
			.boardTypeId(BoardType.INQUIRIES.id())
			.isSecret(true)
            .secretPassword(secretPassword)
            .build();
    }

    private BoardUpdateRequest boardUpdate(String secretPassword) {
        return BoardUpdateRequest.builder().secretPassword(secretPassword).build();
    }
}
