package com.yogesh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateProfileDTO {

	private String name;
	private String bio;
	private String username;
	private String[] interests;
	private String websiteUrl;
}