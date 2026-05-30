package com.study.backend.file.model;

import java.util.Set;

public class FileTypes {

	private static final Set<String> FREE_BOARD_FILE_TYPES = Set.of("jpeg", "png", "gif", "zip");
	private static final Set<String> GALLERY_FILE_TYPES = Set.of("jpeg", "png", "gif");

	private FileTypes() {}

	public static Set<String> freeBoardFileTypes() {
		return FREE_BOARD_FILE_TYPES;
	}

	public static Set<String> galleryFileTypes() {
		return GALLERY_FILE_TYPES;
	}
}
