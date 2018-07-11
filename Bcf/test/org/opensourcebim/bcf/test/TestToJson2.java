package org.opensourcebim.bcf.test;

/******************************************************************************
 * Copyright (C) 2009-2018  BIMserver.org
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see {@literal<http://www.gnu.org/licenses/>}.
 *****************************************************************************/

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.junit.Test;
import org.opensourcebim.bcf.BcfException;
import org.opensourcebim.bcf.BcfFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TestToJson2 {
	@Test
	public void test() {
		try {
			BcfFile bcfFile = BcfFile.read(Paths.get("testdata/opmerkingenv1.bcfzip"));
			ObjectNode jsonNode = bcfFile.toJson();
			ObjectMapper OBJECT_MAPPER = new ObjectMapper();
			File output = new File("bcf.json");
			File images = new File("images");
			images.mkdir();
			OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(output, jsonNode);
		} catch (BcfException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}