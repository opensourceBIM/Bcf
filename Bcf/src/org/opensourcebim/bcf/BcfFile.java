package org.opensourcebim.bcf;

import java.io.ByteArrayInputStream;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.commons.io.IOUtils;
import org.opensourcebim.bcf.markup.BimSnippet;
import org.opensourcebim.bcf.markup.Comment;
import org.opensourcebim.bcf.markup.Comment.Viewpoint;
import org.opensourcebim.bcf.markup.Header;
import org.opensourcebim.bcf.markup.Markup;
import org.opensourcebim.bcf.markup.Topic;
import org.opensourcebim.bcf.markup.Topic.DocumentReference;
import org.opensourcebim.bcf.markup.Topic.RelatedTopic;
import org.opensourcebim.bcf.markup.ViewPoint;
import org.opensourcebim.bcf.project.Project;
import org.opensourcebim.bcf.utils.FakeClosingInputStream;
import org.opensourcebim.bcf.version.Version;
import org.opensourcebim.bcf.visinfo.VisualizationInfo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class BcfFile {
	private static Unmarshaller MARKUP_UNMARSHALLER;
	private static Unmarshaller VISUALIZATION_INFO_UNMARSHALLER;
	private static Unmarshaller PROJECT_UNMARSHALLER;
	private static Unmarshaller VERSION_UNMARSHALLER;
	private static Marshaller PROJECT_MARSHALLER;
	private static Marshaller VERSION_MARSHALLER;
	private final Map<UUID, TopicFolder> topicFolders = new LinkedHashMap<UUID, TopicFolder>();
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private Project project;
	private Version version;

	static {
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(Markup.class);
			MARKUP_UNMARSHALLER = jaxbContext.createUnmarshaller();
			
			jaxbContext = JAXBContext.newInstance(VisualizationInfo.class);
			VISUALIZATION_INFO_UNMARSHALLER = jaxbContext.createUnmarshaller();

			jaxbContext = JAXBContext.newInstance(Project.class);
			PROJECT_UNMARSHALLER = jaxbContext.createUnmarshaller();
			PROJECT_MARSHALLER = jaxbContext.createMarshaller();
			PROJECT_MARSHALLER.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			
			jaxbContext = JAXBContext.newInstance(Version.class);
			VERSION_UNMARSHALLER = jaxbContext.createUnmarshaller();
			VERSION_MARSHALLER = jaxbContext.createMarshaller();
			VERSION_MARSHALLER.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

		} catch (JAXBException e) {
			e.printStackTrace();
		}
	}
	
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

	public static BcfFile read(byte[] data) throws BcfException, IOException {
		BcfFile bcf = new BcfFile();
		bcf.readInternal(new ByteArrayInputStream(data), ReadOptions.DEFAULT);
		return bcf;
	}

	private void readInternal(InputStream inputStream, ReadOptions readOptions) throws BcfException {
		try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
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
							issue.setMarkup((Markup) MARKUP_UNMARSHALLER.unmarshal(new FakeClosingInputStream(zipInputStream)));
						} catch (JAXBException e) {
							throw new BcfException(e);
						}
					} else if (zipEntry.getName().endsWith(".bcfv")) {
						if (readOptions.isReadViewPoints()) {
							try {
								issue.setVisualizationInfo((VisualizationInfo) VISUALIZATION_INFO_UNMARSHALLER.unmarshal(new FakeClosingInputStream(zipInputStream)));
							} catch (JAXBException e) {
								throw new BcfException(e);
							}
						}
					} else if (zipEntry.getName().endsWith("snapshot.png")) {
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						IOUtils.copy(zipInputStream, baos);
						issue.setDefaultSnapShot(baos.toByteArray());
						issue.addSnapShot(zipEntry.getName(), baos.toByteArray());
					} else if (zipEntry.getName().endsWith(".png")) {
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						IOUtils.copy(zipInputStream, baos);
						issue.addSnapShot(zipEntry.getName(), baos.toByteArray());
					}
				} else {
					if (name.equals("project.bcfp")) {
						try {
							project = (Project) PROJECT_UNMARSHALLER.unmarshal(new FakeClosingInputStream(zipInputStream));
						} catch (JAXBException e) {
							throw new BcfException(e);
						}
					} else if (name.equals("bcf.version")) {
						try {
							setVersion((Version) VERSION_UNMARSHALLER.unmarshal(new FakeClosingInputStream(zipInputStream)));
						} catch (JAXBException e) {
							e.printStackTrace();
						}
					} else {
						throw new BcfException("Unexpected zipfile content " + name);
					}
				}
			}
			zipInputStream.close();
		} catch (IOException e) {
			throw new BcfException(e);
		}
	}

	private void setVersion(Version version) {
		this.version = version;
	}

	public Version getVersion() {
		return version;
	}

	public void addTopicFolder(TopicFolder topicFolder) {
		topicFolders.put(topicFolder.getUuid(), topicFolder);
	}

	public static BcfFile read(InputStream inputStream) throws BcfException {
		BcfFile bcf = new BcfFile();
		bcf.readInternal(inputStream, ReadOptions.DEFAULT);
		return bcf;
	}

	public static BcfFile read(InputStream inputStream, ReadOptions readOptions) throws BcfException {
		BcfFile bcf = new BcfFile();
		bcf.readInternal(inputStream, readOptions);
		return bcf;
	}

	public void write(OutputStream outputStream) throws BcfException, IOException {
		ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);

		if (project != null) {
			zipOutputStream.putNextEntry(new ZipEntry("project.bcfp"));
			try {
				PROJECT_MARSHALLER.marshal(project, zipOutputStream);
			} catch (JAXBException e) {
				throw new BcfException(e);
			}
		}

		Version version = new Version();
		version.setVersionId("2.1");
		version.setDetailedVersion("2.1");

		zipOutputStream.putNextEntry(new ZipEntry("bcf.version"));
		try {
			VERSION_MARSHALLER.marshal(version, zipOutputStream);
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

	public TopicFolder createTopicFolder(UUID uuid) {
		TopicFolder topic = new TopicFolder(uuid);
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
			Topic topic = topicFolder.getMarkup().getTopic();
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
		try (InputStream newInputStream = Files.newInputStream(path)) {
			return read(newInputStream);
		}
	}

	public ObjectNode toJson() {
		ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
		if (version != null) {
			ObjectNode versionNode = OBJECT_MAPPER.createObjectNode();
			versionNode.put("detailed", version.getDetailedVersion());
			versionNode.put("id", version.getVersionId());
			objectNode.set("version", versionNode);
		}

		ObjectNode topicsNode = OBJECT_MAPPER.createObjectNode();
		objectNode.set("topics", topicsNode);

		for (UUID uuid : topicFolders.keySet()) {
			TopicFolder topicFolder = topicFolders.get(uuid);
			ObjectNode topicFolderNode = OBJECT_MAPPER.createObjectNode();
			topicFolderNode.put("uuid", uuid.toString());

			Header header = topicFolder.getMarkup().getHeader();
			if (header != null) {
				ObjectNode headerNode = OBJECT_MAPPER.createObjectNode();
				List<org.opensourcebim.bcf.markup.Header.File> files = header.getFile();
				if (files != null) {
					ArrayNode filesNode = OBJECT_MAPPER.createArrayNode();
					headerNode.set("files", filesNode);
					for (org.opensourcebim.bcf.markup.Header.File file : files) {
						ObjectNode fileNode = OBJECT_MAPPER.createObjectNode();
						XMLGregorianCalendar date = file.getDate();
						if (date != null) {
							fileNode.put("date", date.toGregorianCalendar().getTimeInMillis());
						}
						if (file.getFilename() != null) {
							fileNode.put("filename", file.getFilename());
						}
						if (file.getIfcProject() != null) {
							fileNode.put("ifcProject", file.getIfcProject());
						}
						if (file.getIfcSpatialStructureElement() != null) {
							fileNode.put("ifcSpatialStructureElement", file.getIfcSpatialStructureElement());
						}
						if (file.getReference() != null) {
							fileNode.put("reference", file.getReference());
						}
						if (!file.isIsExternal()) {
							fileNode.put("isExternal", false);
						}
						filesNode.add(fileNode);
					}
				}
				topicFolderNode.set("header", headerNode);
			}

			Topic topic = topicFolder.getMarkup().getTopic();
			ObjectNode topicNode = OBJECT_MAPPER.createObjectNode();
			topicFolderNode.set("topic", topicNode);
			topicNode.put("assignedTo", topic.getAssignedTo());
			topicNode.put("creationAuthor", topic.getCreationAuthor());
			topicNode.put("description", topic.getDescription());
			topicNode.put("guid", topic.getGuid());
			topicNode.put("modifiedAuthor", topic.getModifiedAuthor());
			topicNode.put("priority", topic.getPriority());
			ArrayNode referenceLinksNode = OBJECT_MAPPER.createArrayNode();
			for (String referenceLink : topic.getReferenceLink()) {
				referenceLinksNode.add(referenceLink);
			}
			topicNode.set("referenceLinks", referenceLinksNode);
			topicNode.put("title", topic.getTitle());
			topicNode.put("topicStatus", topic.getTopicStatus());
			topicNode.put("topicType", topic.getTopicType());
			BimSnippet bimSnippet = topic.getBimSnippet();
			if (bimSnippet != null) {
				ObjectNode bimSnippetNode = OBJECT_MAPPER.createObjectNode();
				bimSnippetNode.put("reference", bimSnippet.getReference());
				bimSnippetNode.put("referenceSchema", bimSnippet.getReferenceSchema());
				bimSnippetNode.put("snippetType", bimSnippet.getSnippetType());
				topicNode.set("bimSnippet", bimSnippetNode);
			}
			if (topic.getCreationDate() != null) {
				topicNode.put("creationDate", topic.getCreationDate().toGregorianCalendar().getTimeInMillis());
			}
			if (topic.getIndex() != null) {
				topicNode.put("index", topic.getIndex().intValue());
			}
			if (topic.getModifiedDate() != null) {
				topicNode.put("modifiedDate", topic.getModifiedDate().toGregorianCalendar().getTimeInMillis());
			}
			List<DocumentReference> documentReferences = topic.getDocumentReference();
			if (documentReferences != null) {
				ArrayNode documentReferencesNode = OBJECT_MAPPER.createArrayNode();
				for (DocumentReference documentReferences2 : documentReferences) {
					ObjectNode documentReferenceNode = OBJECT_MAPPER.createObjectNode();
					documentReferenceNode.put("description", documentReferences2.getDescription());
					documentReferenceNode.put("guid", documentReferences2.getGuid());
					documentReferenceNode.put("referencedDocument", documentReferences2.getReferencedDocument());
					documentReferencesNode.add(documentReferenceNode);
				}
				topicNode.set("documentReferences", documentReferencesNode);
			}
			List<String> labels = topic.getLabels();
			if (labels != null) {
				ArrayNode labelsNode = OBJECT_MAPPER.createArrayNode();
				for (String label : labels) {
					labelsNode.add(label);
				}
				topicNode.set("labels", labelsNode);
			}
			List<RelatedTopic> relatedTopics = topic.getRelatedTopic();
			if (relatedTopics != null) {
				ArrayNode relatedTopicsNode = OBJECT_MAPPER.createArrayNode();
				for (RelatedTopic relatedTopics2 : relatedTopics) {
					relatedTopicsNode.add(relatedTopics2.getGuid());
				}
				topicNode.set("relatedTopics", relatedTopicsNode);
			}
			List<ViewPoint> viewpoints = topicFolder.getMarkup().getViewpoints();
			if (viewpoints != null) {
				ArrayNode viewPointsNode = OBJECT_MAPPER.createArrayNode();
				for (ViewPoint viewPoint : viewpoints) {
					ObjectNode viewpointNode = OBJECT_MAPPER.createObjectNode();
					viewpointNode.put("snapshot", viewPoint.getSnapshot());
					viewpointNode.put("viewpoint", viewPoint.getViewpoint());
					viewPointsNode.add(viewpointNode);
				}
				topicNode.set("viewpoints", viewPointsNode);
			}
			if (topicFolder.getDefaultSnapShot() != null) {
				topicNode.put("defaultSnapshot", "snapshot.png");
			}
			List<Comment> comments = topicFolder.getMarkup().getComment();
			if (comments != null) {
				ArrayNode commentsNode = OBJECT_MAPPER.createArrayNode();
				for (Comment comment : comments) {
					ObjectNode commentNode = OBJECT_MAPPER.createObjectNode();
					if (comment.getDate() != null) {
						commentNode.put("date", comment.getDate().toGregorianCalendar().getTimeInMillis());
					}
					commentNode.put("author", comment.getAuthor());
					commentNode.put("comment", comment.getComment());

					if (comment.getGuid() != null) {
						commentNode.put("guid", comment.getGuid());
					}
					if (comment.getModifiedAuthor() != null) {
						commentNode.put("modifiedAuthor", comment.getModifiedAuthor());
					}
					if (comment.getModifiedDate() != null) {
						commentNode.put("modifiedDate", comment.getModifiedDate().toGregorianCalendar().getTimeInMillis());
					}
					Viewpoint viewpoint = comment.getViewpoint();
					if (viewpoint != null) {
						ObjectNode viewpointNode = OBJECT_MAPPER.createObjectNode();
						viewpointNode.put("guid", viewpoint.getGuid());
						commentNode.set("viewpoint", viewpointNode);
					}
					commentsNode.add(commentNode);
				}
				topicNode.set("comments", commentsNode);
			}
			topicsNode.set(uuid.toString(), topicFolderNode);
		}
		return objectNode;
	}

	public TopicFolder getTopicFolder(String topicUuid) {
		return topicFolders.get(UUID.fromString(topicUuid));
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("BcfFile\n");
		for (UUID uuid : topicFolders.keySet()) {
			sb.append("\t" + uuid + "\n");
		}
		return sb.toString();
	}

	public void mergeInto(BcfFile otherBcf) {
		for (TopicFolder topicFolder : otherBcf.getTopicFolders()) {
			TopicFolder newTopicFolder = createTopicFolder(topicFolder.getUuid());
			newTopicFolder.setDefaultSnapShot(topicFolder.getDefaultSnapShot());
			newTopicFolder.setMarkup(topicFolder.getMarkup());
			newTopicFolder.setVisualizationInfo(topicFolder.getVisualizationInfo());
		}
	}
}