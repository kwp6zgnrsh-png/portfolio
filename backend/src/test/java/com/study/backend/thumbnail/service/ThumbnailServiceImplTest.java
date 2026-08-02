package com.study.backend.thumbnail.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.study.backend.thumbnail.dto.SourceImage;
import com.study.backend.thumbnail.mapper.ThumbnailMapper;
import com.study.backend.thumbnail.model.ThumbnailMetaData;

@ExtendWith(MockitoExtension.class)
class ThumbnailServiceImplTest {

	@TempDir Path tempDir;

	@Mock ThumbnailMapper thumbnailMapper;

	@Test
	void saveThumbnail_sameSource_createsIndependentFiles() throws Exception {
		Path galleryDirectory = Files.createDirectories(tempDir.resolve("gallery"));
		Files.createDirectories(tempDir.resolve("thumbnail"));
		BufferedImage source = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
		ImageIO.write(source, "png", galleryDirectory.resolve("source.png").toFile());

		ThumbnailServiceImpl thumbnailService = new ThumbnailServiceImpl(thumbnailMapper);
		ReflectionTestUtils.setField(thumbnailService, "storePath", tempDir.toString());
		ReflectionTestUtils.setField(thumbnailService, "thumbnailPath", "thumbnail/");
		SourceImage sourceImage = SourceImage.builder()
			.fileName("source.png")
			.storeName("source")
			.extension(".png")
			.path("gallery/")
			.build();

		Path firstThumbnail = thumbnailService.saveThumbnail(sourceImage, 1L);
		Path secondThumbnail = thumbnailService.saveThumbnail(sourceImage, 2L);

		ArgumentCaptor<ThumbnailMetaData> metadataCaptor = ArgumentCaptor.forClass(ThumbnailMetaData.class);
		then(thumbnailMapper).should(times(2)).createThumbnail(metadataCaptor.capture());
		ThumbnailMetaData firstMetadata = metadataCaptor.getAllValues().get(0);
		ThumbnailMetaData secondMetadata = metadataCaptor.getAllValues().get(1);

		assertThat(firstThumbnail).isNotEqualTo(secondThumbnail);
		assertThat(firstMetadata.getStoreName())
			.isNotEqualTo(sourceImage.storeName())
			.isNotEqualTo(secondMetadata.getStoreName());
		assertThat(secondMetadata.getStoreName()).isNotEqualTo(sourceImage.storeName());
		assertThat(firstThumbnail).hasFileName(firstMetadata.getStoreName() + ".jpeg");
		assertThat(secondThumbnail).hasFileName(secondMetadata.getStoreName() + ".jpeg");
		assertThat(firstThumbnail).exists();
		assertThat(secondThumbnail).exists();
	}
}
