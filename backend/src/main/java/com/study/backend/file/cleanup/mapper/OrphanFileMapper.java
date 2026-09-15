package com.study.backend.file.cleanup.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.study.backend.file.cleanup.model.StoredFileReference;

@Mapper
public interface OrphanFileMapper {

	List<StoredFileReference> findAllReferences();

	List<String> findPendingTaskPaths();

	/** 같은 저장 파일명을 가진 원본·썸네일 참조를 조회한다. */
	List<StoredFileReference> findReferencesByFileName(@Param("fileName") String fileName);

	/** 해당 경로를 원본 또는 목적지로 사용하는 대기 작업이 있는지 확인한다. */
	boolean existsPendingTask(@Param("relativePath") String relativePath);
}