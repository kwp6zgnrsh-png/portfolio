package com.study.backend.thumbnail.mapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

import com.study.backend.common.IntegrationTestBase;

@Transactional
class ThumbnailMapperTest extends IntegrationTestBase {

	@Autowired
	JdbcTemplate jdbcTemplate;

	private Long boardId;

	@BeforeEach
	void setUp() {
		Long memberId = insertMember("thumbuser", "password", "테스터");
		boardId = insertBoard(memberId);
	}

	@Test
	@DisplayName("게시글당 썸네일은 하나만 저장할 수 있다")
	void createThumbnail_duplicateBoardId_throwsDataIntegrityViolationException() {
		insertThumbnail(boardId, "thumb-1");

		assertThatThrownBy(() -> insertThumbnail(boardId, "thumb-2"))
			.isInstanceOf(DataIntegrityViolationException.class);
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

	private Long insertBoard(Long memberId) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(conn -> {
			PreparedStatement ps = conn.prepareStatement(
				"INSERT INTO board(title, content, board_type_id, category_id, member_id) VALUES(?,?,?,?,?)",
				Statement.RETURN_GENERATED_KEYS);
			ps.setString(1, "갤러리 제목");
			ps.setString(2, "갤러리 내용");
			ps.setLong(3, 3L);
			ps.setLong(4, 1L);
			ps.setLong(5, memberId);
			return ps;
		}, keyHolder);
		return keyHolder.getKey().longValue();
	}

	private void insertThumbnail(Long boardId, String storeName) {
		jdbcTemplate.update(conn -> {
			PreparedStatement ps = conn.prepareStatement(
				"INSERT INTO thumbnail(file_name, store_name, extension, path, file_size, board_id) VALUES(?,?,?,?,?,?)");
			ps.setString(1, "thumb.jpeg");
			ps.setString(2, storeName);
			ps.setString(3, ".jpeg");
			ps.setString(4, "/thumbnail/");
			ps.setInt(5, 100);
			ps.setLong(6, boardId);
			return ps;
		});
	}

	private String uniqueMemberId(String memberIdPrefix) {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		String normalizedPrefix = memberIdPrefix.length() > 11 ? memberIdPrefix.substring(0, 11) : memberIdPrefix;
		return normalizedPrefix + "_" + suffix;
	}
}
