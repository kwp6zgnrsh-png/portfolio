package com.study.backend.member.mapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.PreparedStatement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.study.backend.common.IntegrationTestBase;

@Transactional
class MemberMapperTest extends IntegrationTestBase {

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("member_id는 unique 제약으로 중복 저장할 수 없다")
	void createMember_duplicateMemberId_throwsDataIntegrityViolationException() {
		insertMember("duplicate-id", "encoded-password", "홍길동");

		assertThatThrownBy(() -> insertMember("duplicate-id", "encoded-password-2", "임꺽정"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private void insertMember(String memberId, String password, String memberName) {
		jdbcTemplate.update(conn -> {
			PreparedStatement ps = conn.prepareStatement(
				"INSERT INTO member(member_id, member_password, member_name) VALUES(?,?,?)");
			ps.setString(1, memberId);
			ps.setString(2, password);
			ps.setString(3, memberName);
			return ps;
		});
	}
}
