package com.study.backend.board.mapper;

import static org.assertj.core.api.Assertions.*;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

import com.study.backend.board.dto.common.request.BoardUpdateRequest;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.Search;
import com.study.backend.common.IntegrationTestBase;

@Transactional
class FreeBoardMapperTest extends IntegrationTestBase {

    @Autowired FreeBoardMapper freeBoardMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    private Long testMemberId;

    @BeforeEach
    void setUp() {
        testMemberId = insertMember("testuser", "password", "테스터");
    }

    private Long insertMember(String memberIdPrefix, String password, String memberName) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO member(member_id, member_password, member_name) VALUES(?,?,?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, uniqueMemberId(memberIdPrefix));
            ps.setString(2, password);
            ps.setString(3, memberName);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private String uniqueMemberId(String memberIdPrefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String normalizedPrefix = memberIdPrefix.length() > 11 ? memberIdPrefix.substring(0, 11) : memberIdPrefix;
        return normalizedPrefix + "_" + suffix;
    }

    @Test
    @DisplayName("게시글 등록 후 단건 조회 시 같은 제목이 반환된다")
    void createPost_thenGetById_returnsPost() {
        Board board = Board.builder().title("자유 제목").content("자유 내용").categoryId(1L).build();

        freeBoardMapper.createPost(board, 2L, testMemberId);
        Board found = freeBoardMapper.getPostById(board.getId());

        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("자유 제목");
    }

    @Test
    @DisplayName("게시글 등록 후 목록 조회 시 목록에 포함된다")
    void createPost_thenSearchList_containsPost() {
        Board board = Board.builder().title("자유 제목").content("자유 내용").categoryId(1L).build();
        freeBoardMapper.createPost(board, 2L, testMemberId);

        List<Board> result = freeBoardMapper.searchPostList(baseSearch(), 2L, 0);

        assertThat(result).anyMatch(b -> b.getTitle().equals("자유 제목"));
    }

    @Test
    @DisplayName("게시글 수정 후 단건 조회 시 제목이 변경된다")
    void updatePost_thenGetById_titleChanged() {
        Board board = Board.builder().title("원래 제목").content("내용").categoryId(1L).build();
        freeBoardMapper.createPost(board, 2L, testMemberId);

        BoardUpdateRequest update = BoardUpdateRequest.builder()
            .title("수정된 제목").content("수정된 내용").categoryId(1L).build();
        freeBoardMapper.updatePost(board.getId(), update, testMemberId);

        Board found = freeBoardMapper.getPostById(board.getId());
        assertThat(found.getTitle()).isEqualTo("수정된 제목");
    }

    @Test
    @DisplayName("게시글 삭제 후 단건 조회 시 null이 반환된다")
    void deletePost_thenGetById_returnsNull() {
        Board board = Board.builder().title("삭제할 게시글").content("내용").categoryId(1L).build();
        freeBoardMapper.createPost(board, 2L, testMemberId);

        freeBoardMapper.deletePost(board.getId(), testMemberId);

        assertThat(freeBoardMapper.getPostById(board.getId())).isNull();
    }

    @Test
    @DisplayName("게시글 삭제 후 목록 조회 시 목록에 포함되지 않는다")
    void deletePost_thenSearchList_notContainsPost() {
        Board board = Board.builder().title("삭제할 게시글").content("내용").categoryId(1L).build();
        freeBoardMapper.createPost(board, 2L, testMemberId);
        freeBoardMapper.deletePost(board.getId(), testMemberId);

        List<Board> result = freeBoardMapper.searchPostList(baseSearch(), 2L, 0);

        assertThat(result).noneMatch(b -> b.getTitle().equals("삭제할 게시글"));
    }

    @Test
    @DisplayName("게시글 총 개수가 정확히 반환된다")
    void getPostCountByCriteria_returnsCorrectCount() {
        freeBoardMapper.createPost(Board.builder().title("1").content("내용").categoryId(1L).build(), 2L, testMemberId);
        freeBoardMapper.createPost(Board.builder().title("2").content("내용").categoryId(1L).build(), 2L, testMemberId);

        Integer count = freeBoardMapper.getPostCountByCriteria(baseSearch(), 2L);

        assertThat(count).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("조회수 증가 후 views가 1 증가한다")
    void updateViews_incrementsViewCount() {
        Board board = Board.builder().title("제목").content("내용").categoryId(1L).build();
        freeBoardMapper.createPost(board, 2L, testMemberId);
        int before = freeBoardMapper.getPostById(board.getId()).getViews();

        freeBoardMapper.updateViews(board.getId());

        assertThat(freeBoardMapper.getPostById(board.getId()).getViews()).isEqualTo(before + 1);
    }

    private Search baseSearch() {
        return Search.builder()
            .startDate("2020-01-01")
            .endDate("2030-12-31")
            .limit(10)
            .countLimit(1000)
            .page(1)
            .orderByField("createdDate")
            .direction("DESC")
            .build();
    }
}
