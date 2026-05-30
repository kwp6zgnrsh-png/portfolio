package com.study.backend.file.util;

public class PathUtils {

	private PathUtils() {}

	public static String removeLeadingSlash(String path) {
		String normalized = path;
		while (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}
		return normalized;
	}

	public static String ensureTrailingSlash(String path) {
		return path.endsWith("/") ? path : path + "/";
	}

	public static String joinStorePath(String storePath, String path) {
		return ensureTrailingSlash(storePath) + ensureTrailingSlash(removeLeadingSlash(path));
	}

	public static String joinStoreFilePath(String storePath, String path, String storeName, String extension) {
		return joinStorePath(storePath, path) + storeName + extension;
	}
}
