package com.study.backend.board.mapper;

import static org.assertj.core.api.Assertions.*;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

import com.study.backend.board.dto.notice.NoticeList;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.Search;
import com.study.backend.common.IntegrationTestBase;

// 공지사항은 manager가 작성하므로 NoticeMapper에 createPost가 없음
// 테스트 데이터는 JdbcTemplate으로 직접 삽입
@Transactional
class NoticeMapperTest extends IntegrationTestBase {

    @Autowired NoticeMapper noticeMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    private Long testManagerId;

    @BeforeEach
    void setUp() {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO manager(manager_id, manager_password, manager_name) VALUES(?,?,?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, "admin");
            ps.setString(2, "password");
            ps.setString(3, "관리자");
            return ps;
        }, keyHolder);
        testManagerId = keyHolder.getKey().longValue();
    }

    private Long insertNotice(String title, Long categoryId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO board(title, content, board_type_id, category_id, manager_id) VALUES(?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, title);
            ps.setString(2, "공지 내용");
            ps.setLong(3, 1L);
            ps.setLong(4, categoryId);
            ps.setLong(5, testManagerId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Test
    @DisplayName("공지사항 목록 조회 시 등록한 게시글이 포함된다")
    void searchPostList_containsInsertedNotice() {
        insertNotice("공지 제목", 3L);

        List<Board> result = noticeMapper.searchPostList(baseSearch(), 1L, 0);

        assertThat(result).anyMatch(b -> b.getTitle().equals("공지 제목"));
    }

    @Test
    @DisplayName("공지사항 단건 조회 시 올바른 데이터가 반환된다")
    void getPostById_returnsCorrectNotice() {
        Long boardId = insertNotice("단건 공지", 3L);

        Board found = noticeMapper.getPostById(boardId);

        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("단건 공지");
    }

    @Test
    @DisplayName("공지사항 총 개수가 정확히 반환된다")
    void getPostCountByCriteria_returnsCorrectCount() {
        insertNotice("공지1", 3L);
        insertNotice("공지2", 4L);

        Integer count = noticeMapper.getPostCountByCriteria(baseSearch(), 1L);

        assertThat(count).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("알림 카테고리(id=5) 공지사항은 고정 공지 목록에 포함된다")
    void getPinnedNotices_containsPinnedNotice() {
        insertNotice("알림 공지", 5L);

        List<NoticeList> pinned = noticeMapper.getPinnedNotices(5L, 1L);

        assertThat(pinned).anyMatch(n -> n.title().equals("알림 공지"));
    }

    @Test
    @DisplayName("알림 카테고리가 아닌 공지사항은 고정 공지 목록에 포함되지 않는다")
    void getPinnedNotices_notContainsNormalNotice() {
        insertNotice("일반 공지", 3L);

        List<NoticeList> pinned = noticeMapper.getPinnedNotices(5L, 1L);

        assertThat(pinned).noneMatch(n -> n.title().equals("일반 공지"));
    }

    @Test
    @DisplayName("조회수 증가 후 views가 1 증가한다")
    void updateViews_incrementsViewCount() {
        Long boardId = insertNotice("조회수 테스트", 3L);
        int before = noticeMapper.getPostById(boardId).getViews();

        noticeMapper.updateViews(boardId);

        assertThat(noticeMapper.getPostById(boardId).getViews()).isEqualTo(before + 1);
    }

    private Search baseSearch() {
        return Search.builder()
            .startDate(LocalDate.of(2020, 1, 1))
            .endDate(LocalDate.of(2030, 12, 31))
            .limit(10)
            .page(1)
            .orderByField("createdDate")
            .direction("DESC")
            .build();
    }
}
