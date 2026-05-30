package com.study.backend.comment.model;

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
public class Comment {
	private Long id;
	private String content;
	private String createdDate;
	private Long boardId;
	private Long memberId;
	private String author;

}
