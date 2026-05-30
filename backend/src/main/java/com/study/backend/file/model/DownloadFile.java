package com.study.backend.file.model;

import java.io.File;

public record DownloadFile(
	File file,
	FileMetaData fileMetaData
) {
}
