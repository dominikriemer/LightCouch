package org.lightcouch.serializer;

import org.lightcouch.ViewResult;

import java.io.Reader;
import java.util.List;
import java.util.Map;

public interface Serializer<JoT, JeT> {

  String toJson(Object object);

  JeT parseJson(String json);

  JoT parseJsonObject(String json);

  JoT toJsonObject(JeT jsonElement);

  <T> T fromJson(Reader reader, Class<T> type);

  <T> T fromJson(String json, Class<T> type);

  <T> T fromJson(JeT jsonElement, Class<T> type);

  <T> List<T> deserializeAsList(Reader reader, Class<T> listType);

  <T> List<T> deserializeAsList(String json, Class<T> listType);

  List<Map<String, Object>> deserializeAsGenericList(String json);

  Map<String, Object> deserializeAsGenericMap(String json);

  JoT getAsJsonObject(Object object);

  String getId(JoT jsonObject);

  String getRev(JoT jsonObject);

  String getAsString(JoT jsonObject, String key);

  long getAsLong(JoT jsonObject, String key);

  int getAsInt(JoT jsonObject, String key);

  JeT getKeyFromObject(JeT jsonElement, String key);

  String getKeyFromObject(Reader reader, String key);

  <T> void extractDocsToList(Reader reader, Class<T> listType, List<T> list);

  <T> void extractRowToList(Reader reader, Class<T> listType, List<T> list, Boolean includeDocs);

  <T> T getQueryValue(Reader reader, Class<T> type);

  <K, V, T> ViewResult<K, V, T> handleViewResult(Reader reader,
                                                 Boolean includeDocs,
                                                 Class<K> classOfK,
                                                 Class<V> classOfV,
                                                 Class<T> classOfT);
}
