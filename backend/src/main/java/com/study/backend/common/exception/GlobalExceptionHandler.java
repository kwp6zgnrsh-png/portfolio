package com.study.backend.common.exception;

import java.util.stream.Collectors;

import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.study.backend.board.exception.BoardConflictException;
import com.study.backend.board.exception.BoardNotFoundException;
import com.study.backend.board.exception.BoardPermissionDeniedException;
import com.study.backend.board.exception.BoardTypeException;
import com.study.backend.board.exception.InvalidBoardRequestException;
import com.study.backend.comment.exception.CommentNotFoundException;
import com.study.backend.comment.exception.CommentPermissionDeniedException;
import com.study.backend.comment.exception.CommentTargetNotAllowedException;
import com.study.backend.common.dto.ApiResponse;
import com.study.backend.file.exception.FileException;
import com.study.backend.file.exception.FileNotFoundException;
import com.study.backend.file.exception.RequiredFileException;
import com.study.backend.member.exception.DuplicateMemberIdException;
import com.study.backend.member.exception.MemberException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(FileException.class)
	public ApiResponse<Void> fileException(FileException e){
		log.error("FileException", e);
		return ApiResponse.of(e.getMessage());
	}

	@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
	@ExceptionHandler(RequiredFileException.class)
	public ApiResponse<Void> requiredFileException(RequiredFileException e){
		return ApiResponse.of(e.getMessage());
	}

	@ResponseStatus(HttpStatus.NOT_FOUND)
	@ExceptionHandler(FileNotFoundException.class)
	public ApiResponse<Void> fileNotFoundException(FileNotFoundException e){
		return ApiResponse.of(e.getMessage());
	}

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ApiResponse<Void> methodArgumentNotValidException(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors()
			.stream()
			.map(FieldError::getDefaultMessage)
			.collect(Collectors.joining(", "));
		return ApiResponse.of(message);
	}

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(BindException.class)
	public ApiResponse<Void> bindException(BindException e) {
		String message = e.getBindingResult().getAllErrors()
			.stream()
			.map(DefaultMessageSourceResolvable::getDefaultMessage)
			.collect(Collectors.joining(", "));
		return ApiResponse.of(message);
	}

	@ResponseStatus(HttpStatus.NOT_FOUND)
	@ExceptionHandler(CommentNotFoundException.class)
	public ApiResponse<Void> commentNotFoundException(CommentNotFoundException e){
		return ApiResponse.of(e.getMessage());
	}

	@ResponseStatus(HttpStatus.FORBIDDEN)
	@ExceptionHandler(CommentPermissionDeniedException.class)
	public ApiResponse<Void> commentPermissionDeniedException(CommentPermissionDeniedException e){
		return ApiResponse.of(e.getMessage());
	}

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(CommentTargetNotAllowedException.class)
	public ApiResponse<Void> commentTargetNotAllowedException(CommentTargetNotAllowedException e){
		return ApiResponse.of(e.getMessage());
	}

	@ResponseStatus(HttpStatus.NOT_FOUND)
	@ExceptionHandler(BoardNotFoundException.class)
	public ApiResponse<Void> boardNotFoundException(BoardNotFoundException e){
		return ApiResponse.of(e.getMessage());
	}

	@ResponseStatus(HttpStatus.FORBIDDEN)
	@ExceptionHandler(BoardPermissionDeniedException.class)
	public ApiResponse<Void> boardPermissionDeniedException(BoardPermissionDeniedException e){
		return ApiResponse.of(e.getMessage());
	}

	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	@ExceptionHandler(AuthorizationException.class)
	public ApiResponse<Void> authorizationException(AuthorizationException e){
		return ApiResponse.of(e.getMessage());
	}

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(MemberException.class)
	public ApiResponse<Void> memberException(MemberException e){
		return ApiResponse.of(e.getMessage());
	}

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(DuplicateMemberIdException.class)
	public ApiResponse<Void> duplicateMemberIdException(DuplicateMemberIdException e) {
		return ApiResponse.of(e.getMessage());
	}

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ApiResponse<Void> maxUploadSizeExceededException(MaxUploadSizeExceededException e){
		return ApiResponse.of("파일 용량 초과");
	}

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(BoardTypeException.class)
	public ApiResponse<Void> boardTypeException(BoardTypeException e) {
		return ApiResponse.of(e.getMessage());
	}

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(InvalidBoardRequestException.class)
	public ApiResponse<Void> invalidBoardRequestException(InvalidBoardRequestException e) {
		return ApiResponse.of(e.getMessage());
	}

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ApiResponse<Void> dataIntegrityViolationException(DataIntegrityViolationException e) {
		log.error("DataIntegrityViolationException", e);
		return ApiResponse.of("요청 데이터를 처리할 수 없습니다. 입력값을 확인해주세요.");
	}

	@ResponseStatus(HttpStatus.CONFLICT)
	@ExceptionHandler(BoardConflictException.class)
	public ApiResponse<Void> BoardConflictException(BoardConflictException e){
		return ApiResponse.of(e.getMessage());
	}
}
