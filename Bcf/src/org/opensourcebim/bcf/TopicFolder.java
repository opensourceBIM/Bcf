package org.opensourcebim.bcf;

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
	private byte[] defaultSnapShot;
	private Map<String, byte[]> snapshots;
	private Markup markup;
	private VisualizationInfo visualizationInfo;
	private UUID uuid;

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
			JAXBContext jaxbContext = JAXBContext.newInstance(Markup.class);
			Marshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			marshaller.marshal(getMarkup(), zipOutputStream);
		} catch (JAXBException e) {
			throw new BcfException(e);
		}

		ZipEntry visualizationInfo = new ZipEntry(getUuid().toString() + "/viewpoint.bcfv");
		zipOutputStream.putNextEntry(visualizationInfo);
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(VisualizationInfo.class);
			Marshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			marshaller.marshal(getVisualizationInfo(), zipOutputStream);
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
		try {
			setDefaultSnapShot(getClass().getResourceAsStream("dummy.png"));
		} catch (IOException e) {
			e.printStackTrace();
		}
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