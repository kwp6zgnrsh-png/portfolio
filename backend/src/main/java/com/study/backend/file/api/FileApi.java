package com.study.backend.file.api;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.study.backend.board.model.BoardType;
import com.study.backend.common.annotation.Public;
import com.study.backend.file.model.DownloadFile;
import com.study.backend.file.model.FileMetaData;
import com.study.backend.file.service.FileServiceFactory;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FileApi {

	private final FileServiceFactory fileServiceFactory;

	/** 파일 ID로 파일을 조회해 다운로드 응답을 반환한다. 경로 탈출(Path Traversal) 공격을 방어하기 위해 정규 경로를 검증한다. */
	@Public
	@GetMapping("/{boardType}/files/{fileId}")
	public ResponseEntity<FileSystemResource> fileDownload(@PathVariable String boardType,
														   @PathVariable Long fileId) {
		Long boardTypeId = BoardType.from(boardType).id();
		DownloadFile downloadFile = fileServiceFactory.getFileService(boardType).getDownloadFile(fileId, boardTypeId);
		FileMetaData fileMetaData = downloadFile.fileMetaData();
		File file = downloadFile.file();

		String encodedFileName = URLEncoder.encode(fileMetaData.getFileName(), StandardCharsets.UTF_8)
			.replace("+", "%20");

		return ResponseEntity.ok()
			.header("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName)
			.header("X-Content-Type-Options", "nosniff")
			.contentType(MediaType.APPLICATION_OCTET_STREAM)
			.body(new FileSystemResource(file));
	}
}
