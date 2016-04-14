package eu.kaguya.dto;

import java.util.Map;

import lombok.Data;

@Data
public class Currency {
	String base;
	String date;
	Map<String, Double> rates;
}
