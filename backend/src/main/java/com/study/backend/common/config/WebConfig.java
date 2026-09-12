package com.study.backend.common.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.study.backend.file.util.PathUtils;
import com.study.backend.common.interceptor.JwtAuthInterceptor;
import com.study.backend.common.interceptor.LoginRateLimitInterceptor;
import com.study.backend.common.resolver.LoginMemberArgumentResolver;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

	private final JwtAuthInterceptor jwtAuthInterceptor;
	private final LoginRateLimitInterceptor loginRateLimitInterceptor;
	private final LoginMemberArgumentResolver loginMemberArgumentResolver;

	@Value("${app.cors.allowed-origins}")
	private String origins;

	@Value("${store.base-path}")
	private String storePath;

	@Value("${gallery-board.picture.path}")
	private String galleryPicturePath;

	@Value("${gallery-board.thumbnail.path}")
	private String galleryThumbnailPath;

	/** 업로드된 이미지를 /images/{path}/** URL로 서빙하기 위한 정적 리소스 매핑. */
	@Override
	public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
		addImageResourceHandler(registry, galleryPicturePath);
		addImageResourceHandler(registry, galleryThumbnailPath);
	}

	private void addImageResourceHandler(ResourceHandlerRegistry registry, String path) {
		registry.addResourceHandler("/images/" + normalizeResourcePattern(path) + "**")
			.addResourceLocations(normalizeResourceLocation(path));
	}

	private String normalizeResourcePattern(String path) {
		return PathUtils.ensureTrailingSlash(PathUtils.removeLeadingSlash(path));
	}

	private String normalizeResourceLocation(String path) {
		return "file:" + PathUtils.ensureTrailingSlash(storePath) + normalizeResourcePattern(path);
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(loginRateLimitInterceptor)
			.addPathPatterns("/api/login", "/api/sign-up", "/api/*/*/verifySecretPostPassword");
		registry.addInterceptor(jwtAuthInterceptor)
			.addPathPatterns("/**")
			.excludePathPatterns("/swagger-ui/**", "/v3/api-docs/**");
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
			.allowedOrigins(origins)
			.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
			.allowedHeaders("*")
			.allowCredentials(true);
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(loginMemberArgumentResolver);
	}

	@Bean
	public BCryptPasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
