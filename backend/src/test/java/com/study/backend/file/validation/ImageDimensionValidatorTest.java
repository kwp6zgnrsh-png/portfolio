package com.study.backend.file.validation;

import static org.assertj.core.api.AssertionsForClassTypes.*;

import org.junit.jupiter.api.Test;

import com.study.backend.file.exception.FileException;

public class ImageDimensionValidatorTest {

	/** */
	@Test
	void validateDimensions_normalSize_passes() {
		assertThatNoException().isThrownBy(() ->
			ImageDimensionValidator.validateDimensions(3_000, 4_000)
		);
	}

	@Test
	void validateDimensions_widthExceeded_throws() {
		assertThatThrownBy(() ->
			ImageDimensionValidator.validateDimensions(8_001, 1_000)
		).isInstanceOf(FileException.class);
	}

	@Test
	void validateDimensions_totalPixelsExceeded_throws() {
		assertThatThrownBy(() ->
			ImageDimensionValidator.validateDimensions(8_000, 4_000)
		).isInstanceOf(FileException.class);
	}
}
