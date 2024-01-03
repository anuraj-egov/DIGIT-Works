package org.egov.works.mukta.adapter.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Size;
import java.util.List;

import org.egov.common.models.individual.Address;
import org.egov.common.models.individual.Gender;
import org.egov.tracer.model.CustomException;
import org.egov.works.mukta.adapter.constants.Error;

@Getter
@Setter
public class Individual extends ExchangeMessage{
    @JsonProperty("pin")
	String pin;
	
    @JsonProperty("name")
	@Size(min = 1, message = Error.INVALID_NAME)
	String name;

    @JsonProperty("gender")
	@Size(min = 1, message = Error.INVALID_GENDER)
	Gender gender;

	@JsonProperty("address")
	@Size(min = 1, message = Error.INVALID_ADDRESS)
	Address address;

	// @JsonProperty("encoded_photo")
	// @NotBlank(message = Error.INVALID_ENCODED_PHOTO)
	// String encodedPhoto;

    @JsonProperty("email")
	@Size(min = 1, message = Error.INVALID_EMAIL)
	String email;

    @JsonProperty("phone")
	@Size(min = 1, message = Error.INVALID_PHONE)
	String phone;
	
	static public Individual fromString(String json){
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		try {
			return mapper.readValue(json, Individual.class);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			throw new CustomException("Error parsing Identity fromString", e.toString());
		}
	}
}
