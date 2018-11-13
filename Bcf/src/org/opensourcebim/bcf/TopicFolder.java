package org.opensourcebim.bcf;

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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.apache.commons.io.IOUtils;
import org.opensourcebim.bcf.markup.Header;
import org.opensourcebim.bcf.markup.Markup;
import org.opensourcebim.bcf.markup.Topic;
import org.opensourcebim.bcf.visinfo.VisualizationInfo;

public class TopicFolder {
	private static Marshaller MARKUP_MARSHALLER;
	private static Marshaller VISUALIZATION_INFO_MARSHALLER;
	private static byte[] dummyData;
	private byte[] defaultSnapShot;
	private Map<String, byte[]> snapshots;
	private Markup markup;
	private VisualizationInfo visualizationInfo;
	private UUID uuid;
	
	static {
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(Markup.class);
			MARKUP_MARSHALLER = jaxbContext.createMarshaller();
			MARKUP_MARSHALLER.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			
			jaxbContext = JAXBContext.newInstance(VisualizationInfo.class);
			VISUALIZATION_INFO_MARSHALLER = jaxbContext.createMarshaller();
			VISUALIZATION_INFO_MARSHALLER.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
		} catch (JAXBException e) {
			e.printStackTrace();
		}
	}

	public TopicFolder(UUID uuid) {
		this.uuid = uuid;
	}

	public TopicFolder() {
		this.uuid = UUID.randomUUID();
	}
	
	public void setMarkup(Markup markup) {
		this.markup = markup;
	}

	public void setVisualizationInfo(VisualizationInfo visualizationInfo) {
		this.visualizationInfo = visualizationInfo;
	}

	public void setDefaultSnapShot(byte[] defaultSnapShot) {
		this.defaultSnapShot = defaultSnapShot;
	}

	public byte[] getDefaultSnapShot() {
		return defaultSnapShot;
	}

	public VisualizationInfo getVisualizationInfo() {
		if (visualizationInfo == null) {
			visualizationInfo = new VisualizationInfo();
		}
		return visualizationInfo;
	}

	public Markup getMarkup() {
		if (markup == null) {
			markup = new Markup();
		}
		return markup;
	}

	public UUID getUuid() {
		return uuid;
	}

	public void write(ZipOutputStream zipOutputStream) throws IOException, BcfException {
		ZipEntry markup = new ZipEntry(getUuid().toString() + "/markup.bcf");
		zipOutputStream.putNextEntry(markup);
		try {
			MARKUP_MARSHALLER.marshal(getMarkup(), zipOutputStream);
		} catch (JAXBException e) {
			throw new BcfException(e);
		}

		ZipEntry visualizationInfo = new ZipEntry(getUuid().toString() + "/viewpoint.bcfv");
		zipOutputStream.putNextEntry(visualizationInfo);
		try {
			VISUALIZATION_INFO_MARSHALLER.marshal(getVisualizationInfo(), zipOutputStream);
		} catch (JAXBException e) {
			throw new BcfException(e);
		}

		if (getDefaultSnapShot() != null) {
			ZipEntry image = new ZipEntry(getUuid().toString() + "/snapshot.png");
			zipOutputStream.putNextEntry(image);
			ByteArrayInputStream bais = new ByteArrayInputStream(getDefaultSnapShot());
			IOUtils.copy(bais, zipOutputStream);
		}
	}

	public IfcFileReference createIfcFileReference() {
		IfcFileReference ifcFileReference = new IfcFileReference();
		return ifcFileReference;
	}

	public void setDefaultSnapShot(InputStream inputStream) throws IOException {
		defaultSnapShot = IOUtils.toByteArray(inputStream);
	}

	public void setDefaultSnapShotToDummy() {
		setDefaultSnapShot(getDummyData());
	}

	private static byte[] getDummyData() {
		if (dummyData == null) {
			try {
				try (InputStream resourceAsStream = TopicFolder.class.getResourceAsStream("dummy.png")) {
					dummyData = IOUtils.toByteArray(resourceAsStream);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return dummyData;
	}

	public void addSnapShot(String name, byte[] byteArray) {
		if (snapshots == null) {
			snapshots = new HashMap<>();
		}
		snapshots.put(name, byteArray);
	}

	public Header createHeader() {
		Header header = new Header();
		getMarkup().setHeader(header);
		return header;
	}

	public Topic createTopic() {
		Topic topic = new Topic();
		getMarkup().setTopic(topic);
		return topic;
	}

	public byte[] getSnapshot(String name) {
		return snapshots.get(name);
	}
}