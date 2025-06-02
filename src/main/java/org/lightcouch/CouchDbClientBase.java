/*
 * Copyright (C) 2019 indaba.es Copyright (C) 2011 lightcouch.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.lightcouch;

import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.lightcouch.serializer.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.lightcouch.CouchDbUtil.assertNotEmpty;
import static org.lightcouch.CouchDbUtil.assertNull;
import static org.lightcouch.CouchDbUtil.close;
import static org.lightcouch.CouchDbUtil.generateUUID;
import static org.lightcouch.CouchDbUtil.getStream;
import static org.lightcouch.CouchDbUtil.streamToString;
import static org.lightcouch.URIBuilder.buildUri;

/**
 * Contains a client Public API implementation.
 *
 * @see CouchDbClient
 * @author Ahmed Yehia
 */
public abstract class CouchDbClientBase<JoT, JeT> {

    static final Logger log = LoggerFactory.getLogger(CouchDbClient.class);

    private URI baseURI;
    private URI dbURI;
    private Serializer<JoT, JeT> serializer;
    private CouchDbContext<JoT, JeT> context;
    private CouchDbDesign<JoT, JeT> design;
    final CloseableHttpClient httpClient;
    final HttpHost host;

	final BasicCredentialsProvider credentialsProvider;

    CouchDbClientBase(Serializer<JoT, JeT> serializer) {
        this(new CouchDbConfig(), serializer);
    }

    CouchDbClientBase(CouchDbConfig config,
                      Serializer<JoT, JeT> serializer) {
        final CouchDbProperties props = config.getProperties();
        this.credentialsProvider = initializeCredentials(props);
        this.httpClient = createHttpClient(props, credentialsProvider);
        this.serializer = serializer;
        this.host = new HttpHost(props.getProtocol(), props.getHost(), props.getPort());

        final String path = props.getPath() != null ? props.getPath() : "";
        this.baseURI = buildUri().scheme(props.getProtocol()).host(props.getHost()).port(props.getPort()).path("/")
                .path(path).build();
        this.dbURI = buildUri(baseURI).path(props.getDbName()).path("/").build();

        this.context = new CouchDbContext<>(this, props);
        this.design = new CouchDbDesign<>(this);
    }

    // Client(s) provided implementation

    /**
     * @return {@link HttpClient} instance for HTTP request execution.
     */
    abstract CloseableHttpClient createHttpClient(CouchDbProperties properties, CredentialsProvider credentialsProvider);

    /**
     * @return {@link HttpContext} instance for HTTP request execution.
     */
    abstract HttpContext createContext();

    /**
     * Shuts down the connection manager used by this client instance.
     */
    abstract void shutdown();

    abstract BasicCredentialsProvider initializeCredentials(CouchDbProperties props);
    // Public API

    /**
     * Provides access to DB server APIs.
     *
     * @return {@link CouchDbContext}
     */
    public CouchDbContext<JoT, JeT> context() {
        return context;
    }

    /**
     * Provides access to CouchDB Design Documents.
     *
     * @return {@link CouchDbDesign}
     */
    public CouchDbDesign<JoT, JeT> design() {
        return design;
    }

    /**
     * Provides access to CouchDB <tt>View</tt> APIs.
     *
     * @param viewId The view id.
     * @return {@link View}
     */
    public View<JoT, JeT> view(String viewId) {
        return new View<>(this, viewId);
    }

    /**
     * Provides access to <tt>Change Notifications</tt> API.
     *
     * @return {@link Changes}
     */
    public Changes<JoT, JeT> changes() {
        return new Changes<>(this);
    }

    /**
     * Purge operation over database
     *
     * @param toPurge - Map of Ids and the list of revs to purge
     * @return Ids and revs purged
     */
    public PurgeResponse purge(Map<String, List<String>> toPurge) {
        assertNotEmpty(toPurge, "to purge map");
        ClassicHttpResponse response = null;
        Reader reader = null;
        try {
            String jsonToPurge = getSerializer().toJson(toPurge);
            response = post(buildUri(getDBUri()).path("_purge").build(), jsonToPurge);
            reader = new InputStreamReader(getStream(response), StandardCharsets.UTF_8);
            return getSerializer().fromJson(reader, PurgeResponse.class);
        } finally {
            close(reader);
            close(response);
        }
    }

    /**
     * Finds an Object of the specified type.
     *
     * @param <T> Object type.
     * @param classType The class of type T.
     * @param id The document id.
     * @return An object of type T.
     * @throws NoDocumentException If the document is not found in the database.
     */
    public <T> T find(Class<T> classType, String id) {
        assertNotEmpty(classType, "Class");
        assertNotEmpty(id, "id");
        final URI uri = buildUri(getDBUri()).pathEncoded(id).build();
        return get(uri, classType);
    }

    /**
     * Finds an Object of the specified type.
     *
     * @param <T> Object type.
     * @param classType The class of type T.
     * @param id The document id.
     * @param params Extra parameters to append.
     * @return An object of type T.
     * @throws NoDocumentException If the document is not found in the database.
     */
    public <T> T find(Class<T> classType, String id, Params params) {
        assertNotEmpty(classType, "Class");
        assertNotEmpty(id, "id");
        final URI uri = buildUri(getDBUri()).pathEncoded(id).query(params).build();
        return get(uri, classType);
    }

    /**
     * Finds an Object of the specified type.
     *
     * @param <T> Object type.
     * @param classType The class of type T.
     * @param id The document _id field.
     * @param rev The document _rev field.
     * @return An object of type T.
     * @throws NoDocumentException If the document is not found in the database.
     */
    public <T> T find(Class<T> classType, String id, String rev) {
        assertNotEmpty(classType, "Class");
        assertNotEmpty(id, "id");
        assertNotEmpty(id, "rev");
        final URI uri = buildUri(getDBUri()).pathEncoded(id).query("rev", rev).build();
        return get(uri, classType);
    }

    /**
     * This method finds any document given a URI.
     * <p>
     * The URI must be URI-encoded.
     *
     * @param <T> The class type.
     * @param classType The class of type T.
     * @param uri The URI as string.
     * @return An object of type T.
     */
    public <T> T findAny(Class<T> classType, String uri) {
        assertNotEmpty(classType, "Class");
        assertNotEmpty(uri, "uri");
        return get(URI.create(uri), classType);
    }

    /**
     * Finds a document and return the result as {@link InputStream}.
     * <p>
     * <b>Note</b>: The stream must be closed after use to release the connection.
     *
     * @param id The document _id field.
     * @return The result as {@link InputStream}
     * @throws NoDocumentException If the document is not found in the database.
     * @see #find(String, String)
     */
    public InputStream find(String id) {
        assertNotEmpty(id, "id");
        return get(buildUri(getDBUri()).path(id).build());
    }

    /**
     * Finds a document given id and revision and returns the result as {@link InputStream}.
     * <p>
     * <b>Note</b>: The stream must be closed after use to release the connection.
     *
     * @param id The document _id field.
     * @param rev The document _rev field.
     * @return The result as {@link InputStream}
     * @throws NoDocumentException If the document is not found in the database.
     */
    public InputStream find(String id, String rev) {
        assertNotEmpty(id, "id");
        assertNotEmpty(rev, "rev");
        final URI uri = buildUri(getDBUri()).path(id).query("rev", rev).build();
        return get(uri);
    }

    /**
     * Find documents using a declarative JSON querying syntax.
     *
     * @param <T> The class type.
     * @param jsonQuery The JSON query string.
     * @param classOfT The class of type T.
     * @return The result of the query as a {@code List<T> }
     * @throws CouchDbException If the query failed to execute or the request is invalid.
     */
    public <T> List<T> findDocs(String jsonQuery, Class<T> classOfT) {
        assertNotEmpty(jsonQuery, "jsonQuery");
        ClassicHttpResponse response = null;
        try {
            response = post(buildUri(getDBUri()).path("_find").build(), jsonQuery);
            Reader reader = new InputStreamReader(getStream(response), StandardCharsets.UTF_8);
            List<T> list = new ArrayList<T>();
            serializer.extractDocsToList(reader, classOfT, list);
            return list;
        } finally {
            close(response);
        }
    }

    /**
     * Checks if a document exist in the database.
     *
     * @param id The document _id field.
     * @return true If the document is found, false otherwise.
     */
    public boolean contains(String id) {
        assertNotEmpty(id, "id");
        ClassicHttpResponse response = null;
        try {
            response = head(buildUri(getDBUri()).pathEncoded(id).build());
        } catch (NoDocumentException e) {
            return false;
        } finally {
            close(response);
        }
        return true;
    }

    /**
     * Saves an object in the database, using HTTP <tt>PUT</tt> request.
     * <p>
     * If the object doesn't have an <code>_id</code> value, the code will assign a <code>UUID</code> as the document
     * id.
     *
     * @param object The object to save
     * @throws DocumentConflictException If a conflict is detected during the save.
     * @return {@link Response}
     */
    public Response save(Object object) {
        return put(getDBUri(), object, true);
    }

    /**
     * Saves an object in the database using HTTP <tt>POST</tt> request.
     * <p>
     * The database will be responsible for generating the document id.
     *
     * @param object The object to save
     * @return {@link Response}
     */
    public Response post(Object object) {
        assertNotEmpty(object, "object");
        ClassicHttpResponse response = null;
        try {
            URI uri = buildUri(getDBUri()).build();
            response = post(uri, getSerializer().toJson(object));
            return getResponse(response);
        } finally {
            close(response);
        }
    }

    /**
     * Saves a document with <tt>batch=ok</tt> query param.
     *
     * @param object The object to save.
     */
    public void batch(Object object) {
        assertNotEmpty(object, "object");
        ClassicHttpResponse response = null;
        try {
            URI uri = buildUri(getDBUri()).query("batch", "ok").build();
            response = post(uri, getSerializer().toJson(object));
        } finally {
            close(response);
        }
    }

    /**
     * Updates an object in the database, the object must have the correct <code>_id</code> and <code>_rev</code>
     * values.
     *
     * @param object The object to update
     * @throws DocumentConflictException If a conflict is detected during the update.
     * @return {@link Response}
     */
    public Response update(Object object) {
        return put(getDBUri(), object, false);
    }

    /**
     * Removes a document from the database.
     * <p>
     * The object must have the correct <code>_id</code> and <code>_rev</code> values.
     *
     * @param object The document to remove as object.
     * @throws NoDocumentException If the document is not found in the database.
     * @return {@link Response}
     */
    public Response remove(Object object) {
        assertNotEmpty(object, "object");
        var jsonObject = serializer.getAsJsonObject(object);
        final String id = serializer.getId(jsonObject);
        final String rev = serializer.getRev(jsonObject);
        return remove(id, rev);
    }

    /**
     * Removes a document from the database given both a document <code>_id</code> and <code>_rev</code> values.
     *
     * @param id The document _id field.
     * @param rev The document _rev field.
     * @throws NoDocumentException If the document is not found in the database.
     * @return {@link Response}
     */
    public Response remove(String id, String rev) {
        assertNotEmpty(id, "id");
        assertNotEmpty(rev, "rev");
        final URI uri = buildUri(getDBUri()).pathEncoded(id).query("rev", rev).build();
        return delete(uri);
    }

    /**
     * Performs bulk documents create and update request.
     *
     * @param objects The {@link List} of documents objects.
     * @param newEdits If false, prevents the database from assigning documents new revision IDs.
     * @return {@code List<Response>} Containing the resulted entries.
     */
    public List<Response> bulk(List<?> objects, boolean newEdits) {
        assertNotEmpty(objects, "objects");
        ClassicHttpResponse response = null;
        try {
            final String newEditsVal = newEdits ? "\"new_edits\": true, " : "\"new_edits\": false, ";
            final String json = String.format("{%s%s%s}", newEditsVal, "\"docs\": ", getSerializer().toJson(objects));
            final URI uri = buildUri(getDBUri()).path("_bulk_docs").build();
            response = post(uri, json);
            return getResponseList(response);
        } finally {
            close(response);
        }
    }

    /**
     * Saves an attachment to a new document with a generated <tt>UUID</tt> as the document id.
     * <p>
     * To retrieve an attachment, see {@link #find(String)}.
     *
     * @param in The {@link InputStream} holding the binary data.
     * @param name The attachment name.
     * @param contentType The attachment "Content-Type".
     * @return {@link Response}
     */
    public Response saveAttachment(InputStream in, String name, String contentType) {
        assertNotEmpty(in, "in");
        assertNotEmpty(name, "name");
        assertNotEmpty(contentType, "ContentType");
        final URI uri = buildUri(getDBUri()).path(generateUUID()).path("/").path(name).build();
        return put(uri, in, contentType);
    }

    /**
     * Saves an attachment to an existing document given both a document id and revision, or save to a new document
     * given only the id, and rev as {@code null}.
     * <p>
     * To retrieve an attachment, see {@link #find(String)}.
     *
     * @param in The {@link InputStream} holding the binary data.
     * @param name The attachment name.
     * @param contentType The attachment "Content-Type".
     * @param docId The document id to save the attachment under, or {@code null} to save under a new document.
     * @param docRev The document revision to save the attachment under, or {@code null} when saving to a new document.
     * @return {@link Response}
     */
    public Response saveAttachment(InputStream in, String name, String contentType, String docId, String docRev) {
        assertNotEmpty(in, "in");
        assertNotEmpty(name, "name");
        assertNotEmpty(contentType, "ContentType");
        assertNotEmpty(docId, "docId");
        final URI uri = buildUri(getDBUri()).pathEncoded(docId).path("/").path(name).query("rev", docRev).build();
        return put(uri, in, contentType);
    }

    /**
     * removes an attachment from an existing document given a document id and revision and the attachment name
     *
     * @param name The attachment name.
     * @param docId The document id to remove the attachment from
     * @param docRev The document revision to remove the attachment from
     * @return {@link Response}
     */
    public Response removeAttachment(String name, String docId, String docRev) {
        assertNotEmpty(name, "name");
        assertNotEmpty(docId, "docId");
        assertNotEmpty(docRev, "docRev");
        final URI uri = buildUri(getDBUri()).pathEncoded(docId).path("/").path(name).query("rev", docRev).build();
        return delete(uri);
    }

    /**
     * Invokes an Update Handler.
     *
     * <pre>
     * Params params = new Params().addParam("field", "foo").addParam("value", "bar");
     * String output = dbClient.invokeUpdateHandler("designDoc/update1", "docId", params);
     * </pre>
     *
     * @param updateHandlerUri The Update Handler URI, in the format: <code>designDoc/update1</code>
     * @param docId The document id to update.
     * @param params The query parameters as {@link Params}.
     * @return The output of the request.
     */
    public String invokeUpdateHandler(String updateHandlerUri, String docId, Params params) {
        assertNotEmpty(updateHandlerUri, "uri");
        assertNotEmpty(docId, "docId");
        final String[] v = updateHandlerUri.split("/");
        final String path = String.format("_design/%s/_update/%s/", v[0], v[1]);
        final URI uri = buildUri(getDBUri()).path(path).path(docId).query(params).build();
        final ClassicHttpResponse response = executeRequest(new HttpPut(uri));
        return streamToString(getStream(response));
    }

    /**
     * Executes a HTTP request.
     * <p>
     * <b>Note</b>: The response must be closed after use to release the connection.
     *
     * @param request The HTTP request to execute.
     * @return {@link HttpResponse}
     */
    public ClassicHttpResponse executeRequest(ClassicHttpRequest request) {
        try {
            return (ClassicHttpResponse) httpClient.executeOpen(host, request, createContext());
        } catch (IOException e) {
        	// request.abort();
            throw new CouchDbException("Error executing request. ", e);
        }
    }

    /**
     * Synchronize all design documents with the database.
     */
    public void syncDesignDocsWithDb() {
        design().synchronizeAllWithDb();
    }



    /**
     * @return The base URI.
     */
    public URI getBaseUri() {
        return baseURI;
    }

    /**
     * @return The database URI.
     */
    public URI getDBUri() {
        return dbURI;
    }

    /**
     * @return The serializer used by this client.
     */
    public Serializer<JoT, JeT> getSerializer() {
        return serializer;
    }

    // End - Public API

    /**
     * Performs a HTTP GET request.
     *
     * @return {@link InputStream}
     */
    InputStream get(HttpGet httpGet) {
    	ClassicHttpResponse response = executeRequest(httpGet);
        return getStream(response);
    }

    /**
     * Performs a HTTP GET request.
     *
     * @return {@link InputStream}
     */
    InputStream get(URI uri) {
        HttpGet get = new HttpGet(uri);
        get.addHeader("Accept", "application/json");
        return get(get);
    }

    /**
     * Performs a HTTP GET request with given Headers.
     *
     * @return {@link InputStream}
     */
    InputStream get(URI uri, Header[] headers) {
        HttpGet get = new HttpGet(uri);
        get.setHeaders(headers);
        get.addHeader("Accept", "application/json");
        return get(get);
    }

    /**
     * Performs a HTTP GET request.
     *
     * @return An object of type T
     */
    <T> T get(URI uri, Class<T> classType) {
        InputStream in = null;
        try {
            in = get(uri);
            return getSerializer().fromJson(new InputStreamReader(in, "UTF-8"), classType);
        } catch (IOException e) {
            throw new CouchDbException(e);
        } finally {
            close(in);
        }
    }

    /**
     * Performs a HTTP GET request with headers.
     *
     * @return An object of type T
     */
    <T> T get(URI uri, Class<T> classType, Header[] headers) {
        InputStream in = null;
        try {
            in = get(uri, headers);
            return getSerializer().fromJson(new InputStreamReader(in, "UTF-8"), classType);
        } catch (IOException e) {
            throw new CouchDbException(e);
        } finally {
            close(in);
        }
    }

    /**
     * Performs a HTTP HEAD request.
     *
     * @return {@link HttpResponse}
     */
    ClassicHttpResponse head(URI uri) {
        return executeRequest(new HttpHead(uri));
    }

    /**
     * Performs a HTTP PUT request, saves or updates a document.
     *
     * @return {@link Response}
     */
    Response put(URI uri, Object object, boolean newEntity) {
        assertNotEmpty(object, "object");
        ClassicHttpResponse response = null;
        try {
            JoT json = getSerializer().getAsJsonObject(object);
            String id = getSerializer().getId(json);
            String rev = getSerializer().getRev(json);
            if (newEntity) { // save
                assertNull(rev, "rev");
                id = (id == null) ? generateUUID() : id;
            } else { // update
                assertNotEmpty(id, "id");
                assertNotEmpty(rev, "rev");
            }
            final HttpPut put = new HttpPut(buildUri(uri).pathEncoded(id).build());
            setEntity(put, json.toString());
            response = executeRequest(put);
            return getResponse(response);
        } finally {
            close(response);
        }
    }

    /**
     * Performs a HTTP PUT request, saves an attachment.
     *
     * @return {@link Response}
     */
    Response put(URI uri, InputStream instream, String contentType) {
    	ClassicHttpResponse response = null;
        try {
            final HttpPut httpPut = new HttpPut(uri);
            final InputStreamEntity entity = new InputStreamEntity(instream, -1, ContentType.parse(contentType));
            httpPut.setEntity(entity);
            response = executeRequest(httpPut);
            return getResponse(response);
        } finally {
            close(response);
        }
    }

    /**
     * Performs a HTTP POST request.
     *
     * @return {@link HttpResponse}
     */
    ClassicHttpResponse post(URI uri, String json) {
        HttpPost post = new HttpPost(uri);
        setEntity(post, json);
        return executeRequest(post);
    }

    /**
     * Performs a HTTP POST request.
     *
     * @return {@link HttpResponse}
     */
    InputStream post(HttpPost post, String json) {
        setEntity(post, json);
        ClassicHttpResponse resp = executeRequest(post);
        return getStream(resp);
    }

    /**
     * Performs a HTTP POST request.
     *
     * @return An object of type T
     */
    <T> T post(URI uri, String json, Class<T> classType) {
        InputStream in = null;
        try {
            in = getStream(post(uri, json));
            return getSerializer().fromJson(new InputStreamReader(in, "UTF-8"), classType);
        } catch (IOException e) {
            throw new CouchDbException(e);
        } finally {
            close(in);
        }
    }

    /**
     * Performs a HTTP DELETE request.
     *
     * @return {@link Response}
     */
    Response delete(URI uri) {
    	ClassicHttpResponse response = null;
        try {
            HttpDelete delete = new HttpDelete(uri);
            response = executeRequest(delete);
            return getResponse(response);
        } finally {
            close(response);
        }
    }

    // Helpers

    /**
     * Validates a HTTP response; on error cases logs status and throws relevant exceptions.
     *
     * @param response The HTTP response.
     * @throws ParseException 
     */
    void validate(HttpResponse response) throws IOException {
        final int code = response.getCode();
        if (code == 200 || code == 201 || code == 202) { // success (ok | created | accepted)
            return;
        }
        String reason = response.getReasonPhrase();
        switch (code) {
            case HttpStatus.SC_NOT_FOUND: {
                throw new NoDocumentException(reason);
            }
            case HttpStatus.SC_CONFLICT: {
                throw new DocumentConflictException(reason);
            }
            case HttpStatus.SC_NOT_MODIFIED: {
                throw new DocumentNotModifiedException(reason);
            }
            default: { // other errors: 400 | 401 | 500 etc.
                throw new CouchDbException(reason += response.getReasonPhrase());
            }
        }
    }

    /**
     * @param response The {@link HttpResponse}
     * @return {@link Response}
     */
    private Response getResponse(ClassicHttpResponse response) throws CouchDbException {
        InputStreamReader reader = new InputStreamReader(getStream(response), StandardCharsets.UTF_8);
        return getSerializer().fromJson(reader, Response.class);
    }

    /**
     * @param response The {@link HttpResponse}
     * @return {@link Response}
     */
    private List<Response> getResponseList(ClassicHttpResponse response) throws CouchDbException {
        InputStream instream = getStream(response);
        Reader reader = new InputStreamReader(instream, StandardCharsets.UTF_8);
        return getSerializer().deserializeAsList(reader, Response.class);
    }

    /**
     * Sets a JSON String as a request entity.
     *
     * @param httpRequest The request to set entity.
     * @param json The JSON String to set.
     */
    private void setEntity(HttpUriRequestBase httpRequest, String json) {
        StringEntity entity = new StringEntity(json,ContentType.APPLICATION_JSON);
        httpRequest.setEntity(entity);
    }



    /**
     * @param <T>       Object type.
     * @param classType The class of type T.
     * @param id        The document _id field.
     * @param rev       The document revision to check against.
     * @return An Object of type T if it has been modified since the specified revision
     * @throws DocumentNotModifiedException If the document has not been modified
     */
    public <T> T findIfModified(Class<T> classType, String id, String rev) {
        assertNotEmpty(classType, "Class");
        assertNotEmpty(id, "id");
        assertNotEmpty(rev, "rev");

        final URI uri = buildUri(getDBUri()).pathEncoded(id).build();
        Header[] headers = new Header[]{new BasicHeader("If-None-Match", String.format("\"%s\"", rev))};
        return get(uri, classType, headers);
    }

	
}
