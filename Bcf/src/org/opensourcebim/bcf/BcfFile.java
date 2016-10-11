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
import org.opensourcebim.bcf.markup.Topic.DocumentReferences;
import org.opensourcebim.bcf.markup.Topic.RelatedTopics;
import org.opensourcebim.bcf.markup.ViewPoint;
import org.opensourcebim.bcf.project.Project;
import org.opensourcebim.bcf.utils.FakeClosingInputStream;
import org.opensourcebim.bcf.version.Version;
import org.opensourcebim.bcf.visinfo.VisualizationInfo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class BcfFile {
	private final Map<UUID, TopicFolder> topicFolders = new HashMap<UUID, TopicFolder>();
	
	private Project project;

	private Version version;
	
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

	private void readInternal(InputStream inputStream, ReadOptions readOptions) throws BcfException {
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
						if (readOptions.isReadViewPoints()) {
							try {
								JAXBContext jaxbContext = JAXBContext.newInstance(VisualizationInfo.class);
								Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
								issue.setVisualizationInfo((VisualizationInfo)unmarshaller.unmarshal(new FakeClosingInputStream(zipInputStream)));
							} catch (JAXBException e) {
								throw new BcfException(e);
							}
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
					} else if (name.equals("bcf.version")) {
						try {
							JAXBContext jaxbContext = JAXBContext.newInstance(Version.class);
							Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
							setVersion((Version)unmarshaller.unmarshal(new FakeClosingInputStream(zipInputStream)));
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
		return read(Files.newInputStream(path));
	}

	public ObjectNode toJson() {
		ObjectMapper objectMapper = new ObjectMapper();
		ObjectNode objectNode = objectMapper.createObjectNode();
		if (version != null) {
			ObjectNode versionNode = objectMapper.createObjectNode();
			versionNode.put("detailed", version.getDetailedVersion());
			versionNode.put("id", version.getVersionId());
			objectNode.set("version", versionNode);
		}
		
		ObjectNode topicsNode = objectMapper.createObjectNode();
		objectNode.set("topics", topicsNode);
		
		for (UUID uuid : topicFolders.keySet()) {
			TopicFolder topicFolder = topicFolders.get(uuid);
			ObjectNode topicFolderNode = objectMapper.createObjectNode();
			topicFolderNode.put("uuid", uuid.toString());
			
			Header header = topicFolder.getMarkup().getHeader();
			if (header != null) {
				ObjectNode headerNode = objectMapper.createObjectNode();
				List<org.opensourcebim.bcf.markup.Header.File> files = header.getFile();
				if (files != null) {
					ArrayNode filesNode = objectMapper.createArrayNode();
					headerNode.set("files", filesNode);
					for (org.opensourcebim.bcf.markup.Header.File file : files) {
						ObjectNode fileNode = objectMapper.createObjectNode();
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
				objectNode.set("header", headerNode);
			}
			
			Topic topic = topicFolder.getMarkup().getTopic();
			ObjectNode topicNode = objectMapper.createObjectNode();
			topicFolderNode.set("topic", topicNode);
			topicNode.put("assignedTo", topic.getAssignedTo());
			topicNode.put("creationAuthor", topic.getCreationAuthor());
			topicNode.put("description", topic.getDescription());
			topicNode.put("guid", topic.getGuid());
			topicNode.put("modifiedAuthor", topic.getModifiedAuthor());
			topicNode.put("priority", topic.getPriority());
			topicNode.put("referenceLink", topic.getReferenceLink());
			topicNode.put("title", topic.getTitle());
			topicNode.put("topicStatus", topic.getTopicStatus());
			topicNode.put("topicType", topic.getTopicType());
			BimSnippet bimSnippet = topic.getBimSnippet();
			if (bimSnippet != null) {
				ObjectNode bimSnippetNode = objectMapper.createObjectNode();
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
			List<DocumentReferences> documentReferences = topic.getDocumentReferences();
			if (documentReferences != null) {
				ArrayNode documentReferencesNode = objectMapper.createArrayNode();
				for (DocumentReferences documentReferences2 : documentReferences) {
					ObjectNode documentReferenceNode = objectMapper.createObjectNode();
					documentReferenceNode.put("description", documentReferences2.getDescription());
					documentReferenceNode.put("guid", documentReferences2.getGuid());
					documentReferenceNode.put("referencedDocument", documentReferences2.getReferencedDocument());
					documentReferencesNode.add(documentReferenceNode);
				}
				topicNode.set("documentReferences", documentReferencesNode);
			}
			List<String> labels = topic.getLabels();
			if (labels != null) {
				ArrayNode labelsNode = objectMapper.createArrayNode();
				for (String label : labels) {
					labelsNode.add(label);
				}
				topicNode.set("labels", labelsNode);
			}
			List<RelatedTopics> relatedTopics = topic.getRelatedTopics();
			if (relatedTopics != null) {
				ArrayNode relatedTopicsNode = objectMapper.createArrayNode();
				for (RelatedTopics relatedTopics2 : relatedTopics) {
					relatedTopicsNode.add(relatedTopics2.getGuid());
				}
				topicNode.set("relatedTopics", relatedTopicsNode);
			}
			List<ViewPoint> viewpoints = topicFolder.getMarkup().getViewpoints();
			if (viewpoints != null) {
				ArrayNode viewPointsNode = objectMapper.createArrayNode();
				for (ViewPoint viewPoint : viewpoints) {
					ObjectNode viewpointNode = objectMapper.createObjectNode();
					viewpointNode.put("snapshot", viewPoint.getSnapshot());
					viewpointNode.put("viewpoint", viewPoint.getViewpoint());
					viewPointsNode.add(viewpointNode);
				}
				topicNode.set("viewpoints", viewPointsNode);
			}
			List<Comment> comments = topicFolder.getMarkup().getComment();
			if (comments != null) {
				ArrayNode commentsNode = objectMapper.createArrayNode();
				for (Comment comment : comments) {
					ObjectNode commentNode = objectMapper.createObjectNode();
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
					if (comment.getStatus() != null) {
						commentNode.put("status", comment.getStatus());
					}
					if (comment.getVerbalStatus() != null) {
						commentNode.put("verbalStatus", comment.getVerbalStatus());
					}
					if (comment.getModifiedDate() != null) {
						commentNode.put("modifiedDate", comment.getModifiedDate().toGregorianCalendar().getTimeInMillis());
					}
					Viewpoint viewpoint = comment.getViewpoint();
					if (viewpoint != null) {
						ObjectNode viewpointNode = objectMapper.createObjectNode();
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
}