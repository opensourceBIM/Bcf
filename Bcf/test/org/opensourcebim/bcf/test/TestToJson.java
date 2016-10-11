package org.opensourcebim.bcf.test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.junit.Test;
import org.opensourcebim.bcf.BcfException;
import org.opensourcebim.bcf.BcfFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TestToJson {
	@Test
	public void test() {
		try {
			BcfFile bcfFile = BcfFile.read(Paths.get(""));
			ObjectNode jsonNode = bcfFile.toJson();
			ObjectMapper objectMapper = new ObjectMapper();
			File output = new File("bcf.json");
			File images = new File("images");
			images.mkdir();
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(output, jsonNode);
		} catch (BcfException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}