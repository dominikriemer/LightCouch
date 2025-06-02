/*
 * Copyright (C) 2018 indaba.es
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

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpResponse;

import java.io.Closeable;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static java.lang.String.format;

/**
 * Provides various utility methods, for internal use.
 * @author Ahmed Yehia
 */
final class CouchDbUtil {

  private static final String MSG_NOT_NULL = "%s may not be null.";
  private static final String MSG_NOT_EMPTY = "%s may not be empty.";
    
  private CouchDbUtil() {
		// Utility class
	}
	
	@SuppressWarnings("rawtypes")
    public static void assertNotEmpty(Object object, String prefix) throws IllegalArgumentException {
		if(object == null) {
			throw new IllegalArgumentException(format(MSG_NOT_NULL, prefix));
		} else if(object instanceof String && ((String)object).length() == 0) {
			throw new IllegalArgumentException(format(MSG_NOT_EMPTY, prefix));
		} else if(object instanceof Collection && ((Collection)object).isEmpty()) {
            throw new IllegalArgumentException(format(MSG_NOT_EMPTY, prefix));
        } else if(object instanceof Map && ((Map)object).isEmpty()) {
            throw new IllegalArgumentException(format(MSG_NOT_EMPTY, prefix));
        }
	}
	
	public static void assertNull(Object object, String prefix) throws IllegalArgumentException {
		if(object != null) {
			throw new IllegalArgumentException(format(MSG_NOT_NULL, prefix));
		} 
	}
	
	public static void assertTrue(boolean expression, String message) throws IllegalArgumentException {
	    if (!expression) {
	        throw new IllegalArgumentException(format(message));
	    }
	}
	
	public static String generateUUID() {
		return UUID.randomUUID().toString().replace("-", "");
	}
	
	// Files
	
	private static final String LINE_SEP = System.getProperty("line.separator");
	
	private static final String SPRING_BOOT_DIR = "BOOT-INF/classes/";
	
	/**
	 * List directory contents for a resource folder. Not recursive.
	 * This is basically a brute-force implementation.
	 * Works for regular files and also JARs.
	 * 
	 * @author Greg Briggs
	 * @param path Should end with "/", but not start with one.
	 * @return Just the name of each member item, not the full paths.
	 */
	public static List<String> listResources(String path)  {
		JarFile jar = null;
		try {
			Class<CouchDbUtil> clazz = CouchDbUtil.class;
			URL dirURL = clazz.getClassLoader().getResource(path);
			if (dirURL != null && dirURL.getProtocol().equals("file")) {
				return Arrays.asList(new File(dirURL.toURI()).list());
			}
			if (dirURL != null && dirURL.getProtocol().equals("jar")) {
				String jarPath = dirURL.getPath().substring(5, dirURL.getPath().indexOf("!")); 
				jar = new JarFile(URLDecoder.decode(jarPath, "UTF-8"));
				Enumeration<JarEntry> entries = jar.entries(); 
				Set<String> result = new HashSet<String>(); 
				while(entries.hasMoreElements()) {
					String name = entries.nextElement().getName();
					if (name.startsWith(SPRING_BOOT_DIR)) {
						name = name.substring(SPRING_BOOT_DIR.length());
					}
					if (name.startsWith(path)) { 
						String entry = name.substring(path.length());
						int checkSubdir = entry.indexOf("/");
						if (checkSubdir >= 0) {
							entry = entry.substring(0, checkSubdir);
						}
						if(entry.length() > 0) {
							result.add(entry);
						}
					}
				}
				return new ArrayList<String>(result);
			} 
			return null;
		} catch (Exception e) {
			throw new CouchDbException(e);
		}finally {
			close(jar);
		}
	}

	public static String readFile(String path) {
		InputStream instream = CouchDbUtil.class.getResourceAsStream(path);
		StringBuilder content = new StringBuilder();
		Scanner scanner = null;
		try {
			scanner = new Scanner(instream);
			while(scanner.hasNextLine()) {        
				content.append(scanner.nextLine() + LINE_SEP);
			}
		} finally {
			close(instream);
			close(scanner);
		}
		return content.toString();
	}
	
	/**
	 * @return {@link InputStream} of {@link HttpResponse}
	 */
	public static InputStream getStream(ClassicHttpResponse response) {
		try { 
			return response.getEntity().getContent();
		} catch (Exception e) {
			throw new CouchDbException("Error reading response. ", e);
		}
	}
	
	public static String removeExtension(String fileName) {
		return fileName.substring(0, fileName.lastIndexOf('.'));
	}
	
	public static String streamToString(InputStream in) {
	    Scanner s = new Scanner(in);
	    s.useDelimiter("\\A");
	    String str = s.hasNext() ? s.next() : null;
	    close(in);
	    close(s);
	    return str;
	}
	
	/**
	 * Closes the response input stream.
	 * 
	 * @param response The {@link HttpResponse}
	 */
	public static void close(ClassicHttpResponse response) {
		try {
			close(response.getEntity().getContent());
		} catch (Exception e) {}
	}
	
	/**
	 * Closes a resource.
	 * 
	 * @param c The {@link Closeable} resource.
	 */
	public static void close(Closeable c) {
		try {
			c.close();
		} catch (Exception e) {}
	}
}
