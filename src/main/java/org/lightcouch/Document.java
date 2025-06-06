/*
 * Copyright (C) 2011 lightcouch.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lightcouch;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Convenient base class for CouchDB documents, defines the basic 
 * <code>id</code>, <code>revision</code> properties, and attachments.
 * @since 0.0.2
 * @author Ahmed Yehia
 *
 */
public class Document {
	
	@JsonProperty("_id")
	private String id;
	@JsonProperty("_rev")
	private String revision;
	@JsonProperty("_attachments")
	private Map<String, Attachment> attachments; 

	public String getId() {
		return id;
	}

	public String getRevision() {
		return revision;
	}
	
	public Map<String, Attachment> getAttachments() {
		return attachments;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setRevision(String revision) {
		this.revision = revision;
	}
	
	public void setAttachments(Map<String, Attachment> attachments) {
		this.attachments = attachments;
	}

	public Document() {
	}

	/**
	 * Copy contructor that does a deep copy
	 * @param other The document to copy.
	 */
	public Document(Document other) {
		setId(other.getId());
		setRevision(other.getRevision());
		if (other.getAttachments() != null) {
			for(Map.Entry<String, Attachment> entry: other.getAttachments().entrySet()) {
				Attachment attachment = entry.getValue();
				// Attachments are not imutable so we need to copy them.
				Attachment copy = new Attachment(attachment.getData(), attachment.getContentType());
				addAttachment(entry.getKey(), copy);
			}
		}
	}

	
	/**
	 * Adds an in-line document attachment.
	 * @param name The attachment file name
	 * @param attachment The attachment instance
	 */
	public void addAttachment(String name, Attachment attachment) {
		if(attachments == null)
			attachments = new HashMap<String, Attachment>(); 
		attachments.put(name, attachment);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Document other = (Document) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}
}
