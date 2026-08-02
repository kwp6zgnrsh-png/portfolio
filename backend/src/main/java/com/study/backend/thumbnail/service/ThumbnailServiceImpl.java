package com.study.backend.thumbnail.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import net.coobird.thumbnailator.Thumbnails;

import com.study.backend.file.exception.FileException;
import com.study.backend.file.util.PathUtils;
import com.study.backend.thumbnail.dto.SourceImage;
import com.study.backend.thumbnail.mapper.ThumbnailMapper;
import com.study.backend.thumbnail.model.ThumbnailMetaData;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailServiceImpl implements ThumbnailService {

	private final ThumbnailMapper thumbnailMapper;

	@Value("${store.base-path}")
	private String storePath;

	@Value("${gallery-board.thumbnail.path}")
	private String thumbnailPath;

	/** 게시글 ID에 해당하는 썸네일 메타데이터를 조회한다. */
	@Override
	public ThumbnailMetaData getThumbnailByBoardId(Long boardId) {
		return thumbnailMapper.getThumbnailByBoardId(boardId);
	}

	/** 게시글 ID에 해당하는 썸네일 메타데이터를 DB에서 삭제한다. */
	@Override
	public void deleteThumbnail(Long boardId) {
		thumbnailMapper.deleteThumbnail(boardId);
	}

	/** 원본 이미지를 JPEG으로 변환·저장하고 썸네일 메타데이터를 DB에 등록한다. 원본 포맷과 관계없이 항상 JPEG으로 저장한다. */
	@Override
	public Path saveThumbnail(SourceImage sourceImage, Long boardId) {
		String thumbnailStoreName = UUID.randomUUID().toString();
		String absolutePath = PathUtils.joinStoreFilePath(storePath, thumbnailPath, thumbnailStoreName, ".jpeg");
		Path savedThumbnailPath = Path.of(absolutePath);
		long fileSize = saveThumbnailFile(sourceImage, absolutePath);

		ThumbnailMetaData thumbnailMeta = ThumbnailMetaData.builder()
			.fileName(sourceImage.fileName())
			.storeName(thumbnailStoreName)
			.extension(".jpeg")
			.path(thumbnailPath)
			.fileSize(fileSize)
			.boardId(boardId)
			.build();

		try {
			thumbnailMapper.createThumbnail(thumbnailMeta);
			return savedThumbnailPath;
		} catch (Exception e) {
			deleteThumbnailFile(savedThumbnailPath);
			throw e;
		}
	}

	/** Thumbnailator로 원본 파일을 360x360 JPEG으로 변환해 썸네일 저장 경로에 저장하고 파일 크기를 반환한다. */
	private long saveThumbnailFile(SourceImage thumbnailInfo, String absolutePath) {
		File thumbnail = new File(absolutePath);
		try {
			File originFile = new File(PathUtils.joinStoreFilePath(
				storePath,
				thumbnailInfo.path(),
				thumbnailInfo.storeName(),
				thumbnailInfo.extension()
			));

			Thumbnails.of(originFile)
				.size(360,360)
				.keepAspectRatio(true)
				.outputQuality(0.5)
				.outputFormat("jpeg")
				.toFile(thumbnail);

			return thumbnail.length();

		} catch (IOException e) {
			deleteThumbnailFile(thumbnail.toPath());
			throw new FileException("썸네일 저장에 실패했습니다.", e);
		}
	}

	private void deleteThumbnailFile(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			log.error("썸네일 파일 정리 실패: {}", path, e);
		}
	}

}
