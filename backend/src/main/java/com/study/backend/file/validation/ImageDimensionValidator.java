package com.study.backend.file.validation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.study.backend.file.exception.FileException;

public final class ImageDimensionValidator {

	private ImageDimensionValidator() {
	}

	private static final int MAX_IMAGE_WIDTH = 8_000;
	private static final int MAX_IMAGE_HEIGHT = 8_000;
	private static final long MAX_IMAGE_PIXELS = 25_000_000L;

	public static void validate(Path imagePath) {
		// ImageReader로 width, height만 읽기
		// 최대 가로, 세로, 전체 픽셀 검사
		try(ImageInputStream imageInput = ImageIO.createImageInputStream(imagePath.toFile())) {
			if (imageInput == null) {
				throw new FileException("파일 형식 오류");
			}

			Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);

			if(!readers.hasNext()) {
				throw new FileException("파일 형식 오류");
			}

			ImageReader reader = readers.next();

			try {
				reader.setInput(imageInput, true, true);

				int width = reader.getWidth(0);
				int height = reader.getHeight(0);

				validateDimensions(width, height);
			}finally {
				reader.dispose();
			}
		} catch (IOException e) {
			throw new FileException("이미지 정보를 읽을 수 없습니다.", e);
		}
	}

	static void validateDimensions(int width, int height) {
		if (width <= 0 || height <= 0) {
			throw new FileException("파일 형식 오류");
		}

		long totalPixels = (long) width * height;

		if (width > MAX_IMAGE_WIDTH
			|| height > MAX_IMAGE_HEIGHT
			|| totalPixels > MAX_IMAGE_PIXELS) {

			throw new FileException("이미지 해상도가 너무 큽니다");
		}
	}
}
