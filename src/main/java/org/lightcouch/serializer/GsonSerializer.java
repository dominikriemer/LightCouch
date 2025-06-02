package org.lightcouch.serializer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.FieldNamingStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import org.lightcouch.NoDocumentException;
import org.lightcouch.ViewResult;

import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class GsonSerializer implements Serializer<JsonObject, JsonElement> {

  private final Gson gson;

  public GsonSerializer() {
    this.gson = initGson(new GsonBuilder());
  }

  public GsonSerializer(GsonBuilder gsonBuilder) {
    this.gson = initGson(gsonBuilder);
  }

  @Override
  public String toJson(Object object) {
    return gson.toJson(object);
  }

  @Override
  public JsonElement parseJson(String json) {
    return JsonParser.parseString(json);
  }

  @Override
  public JsonObject parseJsonObject(String json) {
    return parseJson(json).getAsJsonObject();
  }

  @Override
  public JsonObject toJsonObject(JsonElement jsonElement) {
    return jsonElement.getAsJsonObject();
  }

  @Override
  public <T> T fromJson(Reader reader, Class<T> type) {
    return gson.fromJson(reader, type);
  }

  @Override
  public <T> T fromJson(String json, Class<T> type) {
    return gson.fromJson(json, type);
  }

  @Override
  public <T> T fromJson(JsonElement jsonElement, Class<T> type) {
    return gson.fromJson(jsonElement, type);
  }

  @Override
  public <T> List<T> deserializeAsList(Reader reader, Class<T> listType) {
    return gson.fromJson(reader, TypeToken.getParameterized(List.class, listType).getType());
  }

  @Override
  public <T> List<T> deserializeAsList(String json, Class<T> listType) {
    return gson.fromJson(json, TypeToken.getParameterized(List.class, listType).getType());
  }

  @Override
  public List<Map<String, Object>> deserializeAsGenericList(String json) {
    return gson.fromJson(json, new TypeToken<List<Map<String, Object>>>() {}.getType());
  }

  @Override
  public Map<String, Object> deserializeAsGenericMap(String json) {
    return gson.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
  }

  @Override
  public JsonObject getAsJsonObject(Object object) {
    return gson.toJsonTree(object).getAsJsonObject();
  }

  @Override
  public String getId(JsonObject jsonObject) {
    return getAsString(jsonObject,"_id");
  }

  @Override
  public String getRev(JsonObject jsonObject) {
    return getAsString(jsonObject,"_rev");
  }

  @Override
  public String getAsString(JsonObject j, String key) {
    return (j.get(key) == null || j.get(key).isJsonNull()) ? null : j.get(key).getAsString();
  }

  @Override
  public long getAsLong(JsonObject j, String key) {
    return (j.get(key) == null || j.get(key).isJsonNull()) ? 0L : j.get(key).getAsLong();
  }

  @Override
  public int getAsInt(JsonObject j, String key) {
    return (j.get(key) == null || j.get(key).isJsonNull()) ? 0 : j.get(key).getAsInt();
  }

  @Override
  public JsonElement getKeyFromObject(JsonElement jsonElement, String key) {
    return jsonElement.getAsJsonObject().get(key);
  }

  @Override
  public String getKeyFromObject(Reader reader, String key) {
    return getAsString(JsonParser.parseReader(reader).getAsJsonObject(), "version");
  }

  @Override
  public <T> void extractDocsToList(Reader reader, Class<T> listType, List<T> list) {
    JsonArray jsonArray = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("docs");
    for (JsonElement jsonElem : jsonArray) {
      JsonElement elem = jsonElem.getAsJsonObject();
      T t = gson.fromJson(elem, listType);
      list.add(t);
    }
  }

  @Override
  public <T> void extractRowToList(Reader reader, Class<T> listType, List<T> list, Boolean includeDocs) {
    JsonArray jsonArray = JsonParser.parseReader(reader)
        .getAsJsonObject().getAsJsonArray("rows");
    for (JsonElement jsonElem : jsonArray) {
      JsonElement elem = jsonElem.getAsJsonObject();
      if(Boolean.TRUE.equals(includeDocs)) {
        elem = jsonElem.getAsJsonObject().get("doc");
      }
      T t = gson.fromJson(elem, listType);
      list.add(t);
    }
  }

  @Override
  public <T> T getQueryValue(Reader reader, Class<T> type) {
    JsonArray array = JsonParser.parseReader(reader).
        getAsJsonObject().get("rows").getAsJsonArray();
    if(array.size() != 1) {
      throw new NoDocumentException("Expecting a single result but was: " + array.size());
    }
    return JsonToObject(array.get(0), "value", type);
  }

  @Override
  public <K, V, T> ViewResult<K, V, T> handleViewResult(Reader reader,
                                                        Boolean includeDocs,
                                                        Class<K> classOfK,
                                                        Class<V> classOfV,
                                                        Class<T> classOfT) {
    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
    ViewResult<K, V, T> vr = new ViewResult<K, V, T>();
    vr.setTotalRows(getAsLong(json, "total_rows"));
    vr.setOffset(getAsInt(json, "offset"));
    vr.setUpdateSeq(getAsString(json, "update_seq"));
    JsonArray jsonArray = json.getAsJsonArray("rows");
    for (JsonElement e : jsonArray) {
      ViewResult<K, V, T>.Rows row = vr.new Rows();
      row.setId(JsonToObject(e, "id", String.class));
      if (classOfK != null) {
        row.setKey(JsonToObject(e, "key", classOfK));
      }
      if (classOfV != null) {
        row.setValue(JsonToObject(e, "value", classOfV));
      }
      if(Boolean.TRUE.equals(includeDocs)) {
        row.setDoc(JsonToObject(e, "doc", classOfT));
      }
      vr.getRows().add(row);
    }
    return vr;
  }

  /**
   * Builds {@link Gson} and registers any required serializer/deserializer.
   *
   * @return {@link Gson} instance
   */
  private Gson initGson(GsonBuilder gsonBuilder) {
    gsonBuilder.registerTypeAdapter(JsonObject.class, new JsonDeserializer<JsonObject>() {
      public JsonObject deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
          throws JsonParseException {
        return json.getAsJsonObject();
      }
    });
    gsonBuilder.registerTypeAdapter(JsonObject.class, new JsonSerializer<JsonObject>() {
      public JsonElement serialize(JsonObject src, Type typeOfSrc, JsonSerializationContext context) {
        return src.getAsJsonObject();
      }

    });
    gsonBuilder.setFieldNamingStrategy(new FieldNamingStrategy() {
      @Override
      public String translateName(Field field) {
        JsonProperty annotation = field.getAnnotation(JsonProperty.class);
        if (annotation != null && !annotation.value().isEmpty()) {
          return annotation.value();
        }
        return field.getName();
      }
    });
    return gsonBuilder.create();
  }

  public <T> T JsonToObject(JsonElement elem, String key, Class<T> classType) {
    return gson.fromJson(getKeyFromObject(elem, key), classType);
  }
}
