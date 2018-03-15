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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.GregorianCalendar;
import java.util.UUID;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

import org.junit.Assert;
import org.junit.Test;
import org.opensourcebim.bcf.BcfException;
import org.opensourcebim.bcf.BcfFile;
import org.opensourcebim.bcf.BcfValidationException;
import org.opensourcebim.bcf.TopicFolder;
import org.opensourcebim.bcf.markup.Header;
import org.opensourcebim.bcf.markup.Header.File;
import org.opensourcebim.bcf.markup.Topic;

public class TestReadWrite {
	@Test
	public void testReadWrite() throws DatatypeConfigurationException, BcfValidationException, BcfException, IOException {
		BcfFile bcfFile = new BcfFile();
		
		bcfFile.getProject().setProjectId("ProjectId-Test");
		bcfFile.getProject().setName("ProjectName-Test");
		
		TopicFolder topicFolder = bcfFile.createTopicFolder();
		Header header = topicFolder.createHeader();
		
		File file = new File();
		file.setFilename("test.ifc");
		file.setIfcProject("");
		file.setIfcSpatialStructureElement("");
		file.setIsExternal(true);
		file.setReference("http://bimserver.org/test");
		
		GregorianCalendar now = new GregorianCalendar();
		file.setDate(DatatypeFactory.newInstance().newXMLGregorianCalendar(now));
		
		header.getFile().add(file);
		
		topicFolder.setDefaultSnapShot(getClass().getResourceAsStream("building.png"));
		Topic topic = topicFolder.createTopic();
		topic.setGuid(UUID.randomUUID().toString());
		topic.setTitle("test");
		topic.setCreationDate(DatatypeFactory.newInstance().newXMLGregorianCalendar(now));
		topic.setCreationAuthor("Ruben");
		
		bcfFile.validate();
		
		Path path = Paths.get("bcftest.zip");
		bcfFile.write(path);
		
		BcfFile readBcf = BcfFile.read(path);

		Assert.assertEquals("ProjectId", bcfFile.getProject().getProjectId(), readBcf.getProject().getProjectId());
		Assert.assertEquals("ProjectName", bcfFile.getProject().getName(), readBcf.getProject().getName());
	}
}
