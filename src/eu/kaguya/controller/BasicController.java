package eu.kaguya.controller;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import eu.kaguya.dto.Currency;
import eu.kaguya.service.BasicService;

@RestController
@RequestMapping("/")
public class BasicController {
	
	@Autowired
	BasicService service;

	@RequestMapping("test")
	public Currency test() throws IOException{
		return service.test();
	}
	
}
