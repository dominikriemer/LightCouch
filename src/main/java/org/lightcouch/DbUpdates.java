/*
 * Copyright (C) 2018 indaba.es
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

public class DbUpdates {

	private List<DbUpdatesResult> results;

	@JsonProperty("last_seq")
	private String lastSeq;

	public List<DbUpdatesResult> getResults() {
		return results;
	}

	public void setResults(List<DbUpdatesResult> results) {
		this.results = results;
	}

	public String getLastSeq() {
		return lastSeq;
	}

	public void setLastSeq(String lastSeq) {
		this.lastSeq = lastSeq;
	}
}
