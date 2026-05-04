package com.scimapp.backend.scim;

import org.springframework.http.HttpStatus;

public class ScimException extends RuntimeException {

	private final HttpStatus status;
	private final String scimType;

	public ScimException(HttpStatus status, String scimType, String detail) {
		super(detail);
		this.status = status;
		this.scimType = scimType;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getScimType() {
		return scimType;
	}
}
