package com.study.backend.board.mapper;

import static org.assertj.core.api.Assertions.*;

import java.io.InputStream;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.study.backend.board.model.Search;

class BoardMapperOrderByTest {

	@ParameterizedTest(name = "{1}.{2} {5}")
	@MethodSource("customSortCases")
	void searchPostList_customSort_addsIdTieBreaker(
		String resource,
		String namespace,
		String orderByField,
		String innerColumn,
		String outerColumn,
		String direction
	) throws Exception {
		Configuration configuration = new Configuration();
		try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
			XMLMapperBuilder mapperBuilder = new XMLMapperBuilder(
				inputStream,
				configuration,
				resource,
				configuration.getSqlFragments()
			);
			mapperBuilder.parse();
		}

		Search search = Search.builder()
			.orderByField(orderByField)
			.direction(direction)
			.limit(10)
			.build();
		Map<String, Object> parameters = Map.of(
			"search", search,
			"boardTypeId", 2L,
			"offset", 0
		);
		String sql = configuration
			.getMappedStatement(namespace + ".searchPostList")
			.getBoundSql(parameters)
			.getSql()
			.replaceAll("\\s+", " ")
			.trim();

		assertThat(sql)
			.containsPattern(orderByPattern(innerColumn, "b2.id", direction))
			.containsPattern(orderByPattern(outerColumn, "b.id", direction));
	}

	private static String orderByPattern(String primaryColumn, String idColumn, String direction) {
		return "ORDER BY\\s+" + Pattern.quote(primaryColumn)
			+ "\\s+" + direction
			+ "\\s*,\\s*" + Pattern.quote(idColumn)
			+ "\\s+" + direction;
	}

	private static Stream<Arguments> customSortCases() {
		return Stream.of(
			mapperCase("FreeBoardMapper", "title", "b2.title", "b.title", "ASC"),
			mapperCase("FreeBoardMapper", "views", "b2.views", "b.views", "DESC"),
			mapperCase("FreeBoardMapper", "categoryName", "c2.category_name", "c.category_name", "ASC"),
			mapperCase("GalleryMapper", "title", "b2.title", "b.title", "ASC"),
			mapperCase("GalleryMapper", "views", "b2.views", "b.views", "DESC"),
			mapperCase("GalleryMapper", "categoryName", "c2.category_name", "c.category_name", "ASC"),
			mapperCase("InquiryMapper", "title", "b2.title", "b.title", "ASC"),
			mapperCase("InquiryMapper", "views", "b2.views", "b.views", "DESC"),
			mapperCase("NoticeMapper", "title", "b2.title", "b.title", "ASC"),
			mapperCase("NoticeMapper", "views", "b2.views", "b.views", "DESC"),
			mapperCase("NoticeMapper", "categoryName", "c2.category_name", "c.category_name", "ASC")
		);
	}

	private static Arguments mapperCase(
		String mapperName,
		String orderByField,
		String innerColumn,
		String outerColumn,
		String direction
	) {
		return Arguments.of(
			"mapper/" + mapperName + ".xml",
			"com.study.backend.board.mapper." + mapperName,
			orderByField,
			innerColumn,
			outerColumn,
			direction
		);
	}
}
