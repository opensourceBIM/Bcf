package org.opensourcebim.bcf;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.io.IOUtils;
import org.opensourcebim.bcf.markup.Markup;
import org.opensourcebim.bcf.markup.Topic;
import org.opensourcebim.bcf.project.Project;
import org.opensourcebim.bcf.utils.FakeClosingInputStream;
import org.opensourcebim.bcf.version.Version;
import org.opensourcebim.bcf.visinfo.VisualizationInfo;

public class BcfFile {
	private final Map<UUID, TopicFolder> topicFolders = new HashMap<UUID, TopicFolder>();
	
	private Project project;
	
	public BcfFile() {
	}
	
	public static BcfFile read(File file) throws BcfException, IOException {
		FileInputStream inputStream = new FileInputStream(file);
		try {
			return read(inputStream);
		} finally {
			inputStream.close();
		}
	}

	private void readInternal(InputStream inputStream) throws BcfException {
		ZipInputStream zipInputStream = new ZipInputStream(inputStream);
		try {
			for (ZipEntry zipEntry = zipInputStream.getNextEntry(); zipEntry != null; zipEntry = zipInputStream.getNextEntry()) {
				String name = zipEntry.getName();
				if (name.contains("/")) {
					String uuidString = name.substring(0, name.indexOf("/"));
					UUID uuid = UUID.fromString(uuidString);
					TopicFolder issue = topicFolders.get(uuid);
					if (issue == null) {
						issue = new TopicFolder(uuid);
						topicFolders.put(uuid, issue);
					}
					if (zipEntry.getName().endsWith(".bcf")) {
						try {
							JAXBContext jaxbContext = JAXBContext.newInstance(Markup.class);
							Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
							issue.setMarkup((Markup)unmarshaller.unmarshal(new FakeClosingInputStream(zipInputStream)));
						} catch (JAXBException e) {
							throw new BcfException(e);
						}
					} else if (zipEntry.getName().endsWith(".bcfv")) {
						try {
							JAXBContext jaxbContext = JAXBContext.newInstance(VisualizationInfo.class);
							Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
							issue.setVisualizationInfo((VisualizationInfo)unmarshaller.unmarshal(new FakeClosingInputStream(zipInputStream)));
						} catch (JAXBException e) {
							throw new BcfException(e);
						}
					} else if (zipEntry.getName().equals("snapshot.png")) {
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						IOUtils.copy(zipInputStream, baos);
						issue.setDefaultSnapShot(baos.toByteArray());
					} else if (zipEntry.getName().endsWith(".png")) {
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						IOUtils.copy(zipInputStream, baos);
						issue.addSnapShot(zipEntry.getName(), baos.toByteArray());
					}
				} else {
					if (name.equals("project.bcfp")) {
						try {
							JAXBContext jaxbContext = JAXBContext.newInstance(Project.class);
							Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
							project = (Project) unmarshaller.unmarshal(new FakeClosingInputStream(zipInputStream));
						} catch (JAXBException e) {
							throw new BcfException(e);
						}
					} else {
						throw new BcfException("Unexpected zipfile content");
					}
				}
			}
			zipInputStream.close();
		} catch (IOException e) {
			throw new BcfException(e);
		}
	}
	
	public void addTopicFolder(TopicFolder topicFolder) {
		topicFolders.put(topicFolder.getUuid(), topicFolder);
	}
	
	public static BcfFile read(InputStream inputStream) throws BcfException {
		BcfFile bcf = new BcfFile();
		bcf.readInternal(inputStream);
		return bcf;
	}
	
	public void write(OutputStream outputStream) throws BcfException, IOException {
		ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
		
		if (project != null) {
			zipOutputStream.putNextEntry(new ZipEntry("project.bcfp"));
			try {
				JAXBContext jaxbContext = JAXBContext.newInstance(Project.class);
				Marshaller marshaller = jaxbContext.createMarshaller();
				marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
				marshaller.marshal(project, zipOutputStream);
			} catch (JAXBException e) {
				throw new BcfException(e);
			}
		}

		Version version = new Version();
		version.setDetailedVersion("2.0 RC");
		
		zipOutputStream.putNextEntry(new ZipEntry("bcf.version"));
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(Version.class);
			Marshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			marshaller.marshal(version, zipOutputStream);
		} catch (JAXBException e) {
			throw new BcfException(e);
		}
		
		for (TopicFolder topicFolder : topicFolders.values()) {
			topicFolder.write(zipOutputStream);
		}
		zipOutputStream.finish();
		zipOutputStream.close();
	}
	
	public Collection<TopicFolder> getTopicFolders() {
		return topicFolders.values();
	}
	
	public void write(File file) throws BcfException, IOException {
		FileOutputStream outputStream = new FileOutputStream(file);
		try {
			write(outputStream);
		} finally {
			outputStream.close();
		}
	}

	public byte[] toBytes() throws BcfException, IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		write(out);
		return out.toByteArray();
	}

	public TopicFolder createTopicFolder() {
		TopicFolder topic = new TopicFolder();
		addTopicFolder(topic);
		return topic;
	}

	public Project getProject() {
		if (project == null) {
			project = new Project();
		}
		return project;
	}

	public void validate() throws BcfValidationException {
		for (TopicFolder topicFolder : getTopicFolders()) {
			Topic topic = topicFolder.getTopic();
			if (topic.getGuid() == null || topic.getGuid().trim().equals("")) {
				throw new BcfValidationException("Topic does not have a Guid");
			}
			if (topic.getTitle() == null || topic.getTitle().trim().equals("")) {
				throw new BcfValidationException("Topic " + topic.getGuid() + " does not have a Title");
			}
			if (topic.getCreationDate() == null) {
				throw new BcfValidationException("Topic " + topic.getGuid() + " does not have a CreationDate");
			}
			if (topic.getCreationAuthor() == null || topic.getCreationAuthor().trim().equals("")) {
				throw new BcfValidationException("Topic " + topic.getGuid() + " does not have a CreationAuthor");
			}
			if (topicFolder.getDefaultSnapShot() == null) {
				throw new BcfValidationException("Topic " + topicFolder.getUuid().toString() + " snapshot.png");
			}
		}
	}

	public void write(Path path) throws BcfException, IOException {
		write(Files.newOutputStream(path));
	}

	public static BcfFile read(Path path) throws BcfException, IOException {
		return read(Files.newInputStream(path));
	}
}