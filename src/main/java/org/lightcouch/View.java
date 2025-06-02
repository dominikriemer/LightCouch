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

import org.lightcouch.serializer.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.lightcouch.CouchDbUtil.assertNotEmpty;
import static org.lightcouch.CouchDbUtil.close;
import static org.lightcouch.CouchDbUtil.getStream;

/**
 * This class provides access to the <tt>View</tt> APIs.
 * 
 * <h3>Usage Example:</h3>
 * <pre>
 * {@code
 *  List<Foo> list = dbClient.view("example/foo")
 *	.startKey("start-key")
 *	.endKey("end-key")
 *	.limit(10)
 *	.includeDocs(true)
 *	.query(Foo.class);
 *  
 *  // scalar values
 *  int count = dbClient.view("example/by_tag")
 * 	.key("couchdb")
 * 	.queryForInt(); 
 * 
 * // pagination
 * Page<Foo> page = dbClient.view("example/foo").queryPage(...);
 * }
 * </pre>
 * 
 * @see CouchDbClientBase#view(String)
 * @see ViewResult
 * @since 0.0.2
 * @author Ahmed Yehia
 */
public class View<JoT, JeT> {
	private static final Logger log = LoggerFactory.getLogger(View.class);
	
	// paging param fields
	private static final String START_KEY                = "s_k";
	private static final String START_KEY_DOC_ID         = "s_k_d_i";
	private static final String CURRENT_START_KEY        = "c_k";
	private static final String CURRENT_START_KEY_DOC_ID = "c_k_d_i";
	private static final String CURRENT_KEYS             = "c";
	private static final String ACTION                   = "a";
	private static final String NEXT                     = "n";
	private static final String PREVIOUS                 = "p";
	
	// view fields
	private String key;
	private String startKey;
	private String startKeyDocId;
	private String endKey;
	private String endKeyDocId;
	private Integer limit;
	private String stale;
	private Boolean descending;
	private Integer skip;
	private Boolean group;
	private Integer groupLevel;
	private Boolean reduce;
	private Boolean includeDocs;
	private Boolean inclusiveEnd;
	private Boolean updateSeq;
	
	private CouchDbClientBase<JoT, JeT> dbc;
	private Serializer<JoT, JeT> serializer;
	private URIBuilder uriBuilder;
	
	private String allDocsKeys; // bulk docs
	
	View(CouchDbClientBase<JoT, JeT> dbc, String viewId) {
		assertNotEmpty(viewId, "View id");
		this.dbc = dbc;
		this.serializer = dbc.getSerializer();
		
		String view = viewId;
		if(viewId.contains("/")) {
			String[] v = viewId.split("/");
			view = String.format("_design/%s/_view/%s", v[0], v[1]);
		}
		this.uriBuilder = URIBuilder.buildUri(dbc.getDBUri()).path(view);
	}
	
	// Query options
	
	/**
	 * Queries a view as an {@link InputStream}
	 * <p>The stream should be properly closed after usage, as to avoid connection leaks.
	 * @return The result as an {@link InputStream}.
	 */
	public InputStream queryForStream() {
		URI uri = uriBuilder.build();
		if(allDocsKeys != null) { // bulk docs
			return getStream(dbc.post(uri, allDocsKeys));
		}
		
		return dbc.get(uri);
	}
	
	/**
	 * Queries a view.
	 * @param <T> Object type T
	 * @param classOfT The class of type T
	 * @return The result of the view query as a {@code List<T> }
	 */
	public <T> List<T> query(Class<T> classOfT) {
		InputStream instream = null;
		try {  
			Reader reader = new InputStreamReader(instream = queryForStream(), StandardCharsets.UTF_8);
			List<T> list = new ArrayList<T>();
			serializer.extractRowToList(reader, classOfT, list, this.includeDocs);
			return list;
		} finally {
			close(instream);
		}
	}

	/**
	 * Queries a view.
	 * @param <K> Object type K (key)
	 * @param <V> Object type V (value)
	 * @param <T> The class type
	 * @param classOfK The class of type K.
	 * @param classOfV The class of type V.
	 * @param classOfT The class of type T.
	 * @return The View result entries.
	 */
	public <K, V, T> ViewResult<K, V, T> queryView(Class<K> classOfK, Class<V> classOfV, Class<T> classOfT) {
		InputStream instream = null;
		try {  
			Reader reader = new InputStreamReader(instream = queryForStream(), StandardCharsets.UTF_8);
			return serializer.handleViewResult(reader, includeDocs, classOfK, classOfV, classOfT);
		} finally {
			close(instream);
		}
	}
	
	/**
	 * @return The result of the view as String.
	 */
	public String queryForString() {
		return queryValue(String.class);
	}
	
	/**
	 * @return The result of the view as int.
	 */
	public int queryForInt() {
		return queryValue(int.class);
	}
	
	/**
	 * @return The result of the view as long.
	 */
	public long queryForLong() {
		return queryValue(long.class);
	}
	
	/**
	 * @return The result of the view as boolean.
	 */
	public boolean queryForBoolean() {
		return queryValue(boolean.class);
	}
	
	/**
	 * Queries for scalar values. Internal use.
	 */
	private <V> V queryValue(Class<V> classOfV) {
		InputStream instream = null;
		try {  
			Reader reader = new InputStreamReader(instream = queryForStream(), StandardCharsets.UTF_8);
			return serializer.getQueryValue(reader, classOfV);
		} finally {
			close(instream);
		}
	}
	
	/**
	 * Queries a view for pagination, returns a next or a previous page, this method
	 * figures out which page to return based on the given param that is generated by an
	 * earlier call to this method, quering the first page is done by passing a {@code null} param.
	 * @param <T> Object type T
	 * @param rowsPerPage The number of rows per page.
	 * @param param The request parameter to use to query a page, or {@code null} to return the first page.
	 * @param classOfT The class of type T.
	 * @return {@link Page}
	 */
	public <T> Page<T> queryPage(int rowsPerPage, String param, Class<T> classOfT) {
		if(param == null) { // assume first page
			return queryNextPage(rowsPerPage, null, null, null, null, classOfT);
		}
		String currentStartKey;
		String currentStartKeyDocId;
		String startKey;
		String startKeyDocId;
		String action;
		try {
			// extract fields from the returned HEXed JSON object
			final JeT json = serializer.parseJson(new String(java.util.Base64.getDecoder().decode(param.getBytes())));
			if(log.isDebugEnabled()) {
				log.debug("Paging Param Decoded = " + json);
			}
			final JoT jsonCurrent = serializer.toJsonObject(serializer.getKeyFromObject(json, CURRENT_KEYS));
			currentStartKey = serializer.getAsString(jsonCurrent, CURRENT_START_KEY);
			currentStartKeyDocId = serializer.getAsString(jsonCurrent, CURRENT_START_KEY_DOC_ID);
			startKey = serializer.getAsString(serializer.toJsonObject(json), START_KEY);
			startKeyDocId = serializer.getAsString(serializer.toJsonObject(json), START_KEY_DOC_ID);
			action = serializer.getAsString(serializer.toJsonObject(json), ACTION);
		} catch (Exception e) {
			throw new CouchDbException("could not parse the given param!", e);
		}
		if(PREVIOUS.equals(action)) { // previous
			return queryPreviousPage(rowsPerPage, currentStartKey, currentStartKeyDocId, startKey, startKeyDocId, classOfT);
		} else { // next
			return queryNextPage(rowsPerPage, currentStartKey, currentStartKeyDocId, startKey, startKeyDocId, classOfT);
		}
	}
	
	/**
	 * @return The next page.
	 */
	private <T> Page<T> queryNextPage(int rowsPerPage, String currentStartKey, 
			String currentStartKeyDocId, String startKey, String startKeyDocId, Class<T> classOfT) {
		// set view query params
		limit(rowsPerPage + 1);
		includeDocs(true);
		if(startKey != null) { 
			startKey(startKey);
			startKeyDocId(startKeyDocId);
		}
		// init page, query view
		final Page<T> page = new Page<T>();
		final List<T> pageList = new ArrayList<T>();
		final ViewResult<String, Object, T> vr = queryView(String.class, Object.class, classOfT);
		final List<ViewResult<String, Object, T>.Rows> rows = vr.getRows();
		final int resultRows = rows.size();
		final int offset = vr.getOffset();
		final long totalRows = vr.getTotalRows();
		// holds page params
		final var currentKeys = new HashMap<String, Object>();
		final var jsonNext = new HashMap<String, Object>();
		final var jsonPrev = new HashMap<String, Object>();
		currentKeys.put(CURRENT_START_KEY, rows.get(0).getKey());
		currentKeys.put(CURRENT_START_KEY_DOC_ID, rows.get(0).getId());
		for (int i = 0; i < resultRows; i++) {
			// set keys for the next page
			if (i == resultRows - 1) { // last element (i.e rowsPerPage + 1)
				if(resultRows > rowsPerPage) { // if not last page
					page.setHasNext(true);
					jsonNext.put(START_KEY, rows.get(i).getKey());
					jsonNext.put(START_KEY_DOC_ID, rows.get(i).getId());
					jsonNext.put(CURRENT_KEYS, currentKeys);
					jsonNext.put(ACTION, NEXT);
					page.setNextParam(Base64.getUrlEncoder().encodeToString(serializer.toJson(jsonNext).getBytes()));
					continue; // exclude 
				} 
			}
			pageList.add(rows.get(i).getDoc());
		}
		// set keys for the previous page
		if(offset != 0) { // if not first page
			page.setHasPrevious(true);
			jsonPrev.put(START_KEY, currentStartKey);
			jsonPrev.put(START_KEY_DOC_ID, currentStartKeyDocId);
			jsonPrev.put(CURRENT_KEYS, currentKeys);
			jsonPrev.put(ACTION, PREVIOUS);
			page.setPreviousParam(Base64.getUrlEncoder().encodeToString(serializer.toJson(jsonPrev).getBytes()));
		}
		// calculate paging display info
		page.setResultList(pageList);
		page.setTotalResults(totalRows);
		page.setResultFrom(offset + 1);
		final int resultTo = rowsPerPage > resultRows ? resultRows : rowsPerPage; // fix when rowsPerPage exceeds returned rows
		page.setResultTo(offset + resultTo);
		page.setPageNumber((int) Math.ceil(page.getResultFrom() / Double.valueOf(rowsPerPage)));
		return page;
	}
	
	/**
	 * @return The previous page.
	 */
	private <T> Page<T> queryPreviousPage(int rowsPerPage, String currentStartKey, 
			String currentStartKeyDocId, String startKey, String startKeyDocId, Class<T> classOfT) {
		// set view query params
		limit(rowsPerPage + 1);
		includeDocs(true);
		descending(true); // read backward
		startKey(currentStartKey); 
		startKeyDocId(currentStartKeyDocId); 
		// init page, query view
		final Page<T> page = new Page<T>();
		final List<T> pageList = new ArrayList<T>();
		final ViewResult<String, Object, T> vr = queryView(String.class, Object.class, classOfT);
		final List<ViewResult<String, Object, T>.Rows> rows = vr.getRows();
		final int resultRows = rows.size();
		final int offset = vr.getOffset();
		final long totalRows = vr.getTotalRows();
		Collections.reverse(rows); // fix order
		// holds page params
		final var currentKeys = new HashMap<String, Object>();
		final var jsonNext = new HashMap<String, Object>();
		final var jsonPrev = new HashMap<String, Object>();
		currentKeys.put(CURRENT_START_KEY, rows.get(0).getKey());
		currentKeys.put(CURRENT_START_KEY_DOC_ID, rows.get(0).getId());
		for (int i = 0; i < resultRows; i++) {
			// set keys for the next page
			if (i == resultRows - 1) { // last element (i.e rowsPerPage + 1)
				if(resultRows >= rowsPerPage) { // if not last page
					page.setHasNext(true);
					jsonNext.put(START_KEY, rows.get(i).getKey());
					jsonNext.put(START_KEY_DOC_ID, rows.get(i).getId());
					jsonNext.put(CURRENT_KEYS, currentKeys);
					jsonNext.put(ACTION, NEXT);
					page.setNextParam(Base64.getUrlEncoder().encodeToString(serializer.toJson(jsonNext).getBytes()));
					continue; 
				}
			}
			pageList.add(rows.get(i).getDoc());
		}
		// set keys for the previous page
		if(offset != (totalRows - rowsPerPage - 1)) { // if not first page
			page.setHasPrevious(true);
			jsonPrev.put(START_KEY, currentStartKey);
			jsonPrev.put(START_KEY_DOC_ID, currentStartKeyDocId);
			jsonPrev.put(CURRENT_KEYS, currentKeys);
			jsonPrev.put(ACTION, PREVIOUS);
			page.setPreviousParam(Base64.getUrlEncoder().encodeToString(serializer.toJson(jsonPrev).getBytes()));
		}
		// calculate paging display info
		page.setResultList(pageList);
		page.setTotalResults(totalRows);
		page.setResultFrom((int) totalRows - (offset + rowsPerPage));
		final int resultTo = (int) totalRows - offset - 1;
		page.setResultTo(resultTo);
		page.setPageNumber(resultTo / rowsPerPage);
		return page;
	}
	
	// fields
	
	/**
	 * @param key The key value, accepts a single value or multiple values for complex keys.
	 * @return {@link View}
	 */
	public View key(Object... key) {
		this.key = getKeyAsJson(key);
		uriBuilder.query("key", this.key);
		return this;
	}
	
	/**
	 * @param startKey The start key value, accepts a single value or multiple values for complex keys.
	 * @return {@link View}
	 */
	public View startKey(Object... startKey) {
		this.startKey = getKeyAsJson(startKey);
		uriBuilder.query("startkey", this.startKey);
		return this;
	}
	
	/**
	 * @param startKeyDocId The start key document id.
	 * @return {@link View}
	 */
	public View startKeyDocId(String startKeyDocId) {
		this.startKeyDocId = startKeyDocId;
		uriBuilder.query("startkey_docid", this.startKeyDocId);
		return this;
	}
	
	/**
	 * @param endKey The end key value, accepts a single value or multiple values for complex keys.
	 * @return {@link View}
	 */
	public View endKey(Object... endKey) {
		this.endKey = getKeyAsJson(endKey);
		uriBuilder.query("endkey", this.endKey);
		return this;
	}
	
	/**
	 * @param endKeyDocId The end key document id.
	 * @return {@link View}
	 */
	public View endKeyDocId(String endKeyDocId) {
		this.endKeyDocId = endKeyDocId;
		uriBuilder.query("endkey_docid", this.endKeyDocId);
		return this;
	}
	
	/**
	 * @param limit The limit value.
	 * @return {@link View}
	 */
	public View limit(Integer limit) {
		this.limit = limit;
		uriBuilder.query("limit", this.limit);
		return this;
	}
	
	/**
	 * @param stale Accept values: ok | update_after (update_after as of CouchDB 1.1.0)
	 * @return {@link View}
	 */
	public View stale(String stale) {
		this.stale = stale;
		uriBuilder.query("stale", this.stale);
		return this;
	}
	
	/**
	 * Reverses the reading direction, not the sort order.
	 * @param descending The descending value true | false
	 * @return {@link View}
	 */
	public View descending(Boolean descending) {
		this.descending = Boolean.valueOf(dbc.getSerializer().toJson(descending));
		uriBuilder.query("descending", this.descending);
		return this;
	}
	
	/**
	 * @param skip Skips <i>n</i> number of documents.
	 * @return {@link View}
	 */
	public View skip(Integer skip) {
		this.skip = skip;
		uriBuilder.query("skip", this.skip);
		return this;
	}
	
	/**
	 * @param group Specifies whether the reduce function reduces the result to a set of keys, 
	 * or to a single result. Defaults to false (single result).
	 * @return {@link View}
	 */
	public View group(Boolean group) {
		this.group = group;
		uriBuilder.query("group", this.group);
		return this;
	}
	
	/**
	 * @param groupLevel The group level
	 * @return {@link View}
	 */
	public View groupLevel(Integer groupLevel) {
		this.groupLevel = groupLevel;
		uriBuilder.query("group_level", this.groupLevel);
		return this;
	}
	
	/**
	 * @param reduce Indicates whether to use the reduce function of the view,
	 * defaults to true if the reduce function is defined.
	 * @return {@link View}
	 */
	public View reduce(Boolean reduce) {
		this.reduce = reduce;
		uriBuilder.query("reduce", this.reduce);
		return this;
	}
	
	/**
	 * @param includeDocs Indicates whether to include documents
	 * @return {@link View}
	 */
	public View includeDocs(Boolean includeDocs) {
		this.includeDocs = includeDocs;
		uriBuilder.query("include_docs", this.includeDocs);
		return this;
	}
	
	/**
	 * @param inclusiveEnd Indicates whether the endkey is included in the result, 
	 * defaults to true.
	 * @return {@link View}
	 */
	public View inclusiveEnd(Boolean inclusiveEnd) {
		this.inclusiveEnd = inclusiveEnd;
		uriBuilder.query("inclusive_end", this.inclusiveEnd);
		return this;
	}
	
	/**
	 * @param updateSeq Indicates whether to include sequence id of the view 
	 * @return {@link View}
	 */
	public View updateSeq(Boolean updateSeq) {
		this.updateSeq = updateSeq;
		uriBuilder.query("update_seq", this.updateSeq);
		return this;
	}
	
	/**
	 * Supplies a key list when calling <tt>_all_docs</tt> View.
	 * @param keys The list of keys
	 * @return {@link View}
	 */
	public View keys(List<?> keys) {
		this.allDocsKeys = String.format("{%s:%s}", serializer.toJson("keys"), serializer.toJson(keys));
		return this;
	}
	
	private String getKeyAsJson(Object... key) {
		return (key.length == 1) ? serializer.toJson(key[0]) : serializer.toJson(key); // single or complex key
	}
}
