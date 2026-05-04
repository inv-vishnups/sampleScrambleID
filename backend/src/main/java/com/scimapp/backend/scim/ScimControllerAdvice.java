package com.scimapp.backend.scim;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.scimapp.backend.scim.dto.ScimErrorResponse;

@RestControllerAdvice(assignableTypes = {
		ScimUserController.class,
		ScimGroupController.class,
		ScimServiceProviderConfigController.class,
		ScimResourceTypesController.class,
		ScimSchemasController.class
})
public class ScimControllerAdvice {

	@ExceptionHandler(ScimException.class)
	public ResponseEntity<ScimErrorResponse> handleScim(ScimException ex) {
		ScimErrorResponse body = ScimErrorResponse.of(
				ex.getStatus().value(),
				ex.getScimType(),
				ex.getMessage());
		return ResponseEntity.status(ex.getStatus()).body(body);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ScimErrorResponse> handleBadJson(HttpMessageNotReadableException ex) {
		ScimErrorResponse body = ScimErrorResponse.of(400, "invalidSyntax", "Malformed JSON body");
		return ResponseEntity.badRequest().body(body);
	}
}
