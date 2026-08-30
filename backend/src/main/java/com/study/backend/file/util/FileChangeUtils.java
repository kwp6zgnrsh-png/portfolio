package com.study.backend.file.util;

import org.springframework.web.multipart.MultipartFile;

public class FileChangeUtils {

	private FileChangeUtils() {}

	/** 비어 있지 않은 신규 파일이 하나라도 있는지 확인한다. */
	public static boolean hasNewFiles(MultipartFile[] files) {
		if (files == null || files.length == 0) {
			return false;
		}

		for (MultipartFile file : files) {
			if (file != null && !file.isEmpty()) {
				return true;
			}
		}

		return false;
	}

	/** 기존 파일 삭제 요청이 하나라도 있는지 확인한다. */
	public static boolean hasDeletedFiles(String[] deleteFiles) {
		return deleteFiles != null && deleteFiles.length > 0;
	}
}
