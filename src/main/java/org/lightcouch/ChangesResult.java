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

import java.util.List;
import java.util.Map;

/**
 * Represents Changes feed result of type <i>normal</i>.
 * @since 0.0.2
 * @see Changes
 * @author Ahmed Yehia
 *
 */
public class ChangesResult {
	private List<ChangesResult.Row> results;
	@JsonProperty("last_seq")
	private String lastSeq;

	public List<ChangesResult.Row> getResults() {
		return results;
	}

	public String getLastSeq() {
		return lastSeq;
	}

	/**
	 * Represent a row in Changes result. 
	 */
	public static class Row {
		private String seq;
		private String id;
		private List<Row.Rev> changes;
		private boolean deleted;
		private Map<String, Object> doc;

		public String getSeq() {
			return seq;
		}

		public String getId() {
			return id;
		}

		public List<Row.Rev> getChanges() {
			return changes;
		}

		public boolean isDeleted() {
			return deleted;
		}

		public Map<String, Object> getDoc() {
			return doc;
		}

		/**
		 * Represent a Change rev. 
		 */
		public static class Rev {
			private String rev;

			public String getRev() {
				return rev;
			}
		} // end class Rev
	} // end class Row
} // end class ChangesResult
