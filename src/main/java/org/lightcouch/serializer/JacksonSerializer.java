package org.lightcouch.serializer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.lightcouch.ViewResult;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;

public class JacksonSerializer implements Serializer<ObjectNode, JsonNode> {

  private final ObjectMapper mapper;

  public JacksonSerializer() {
    this.mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

  }

  @Override
  public String toJson(Object object) {
    try {
      return mapper.writeValueAsString(object);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public JsonNode parseJson(String json) {
    try {
      return mapper.readTree(json);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public ObjectNode parseJsonObject(String json) {
    try {
      JsonNode node = mapper.readTree(json);
      if (node instanceof ObjectNode) {
        return (ObjectNode) node;
      }
      throw new IllegalArgumentException("JSON is not an object");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public ObjectNode toJsonObject(JsonNode jsonElement) {
    if (jsonElement instanceof ObjectNode) {
      return (ObjectNode) jsonElement;
    }
    throw new IllegalArgumentException("JsonNode is not an ObjectNode");
  }

  @Override
  public <T> T fromJson(Reader reader, Class<T> type) {
    try {
      return mapper.readValue(reader, type);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public <T> T fromJson(String json, Class<T> type) {
    try {
      return mapper.readValue(json, type);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public <T> T fromJson(JsonNode jsonElement, Class<T> type) {
    try {
      return mapper.treeToValue(jsonElement, type);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public <T> List<T> deserializeAsList(Reader reader, Class<T> listType) {
    try {
      return mapper.readValue(reader, new TypeReference<List<T>>() {});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public <T> List<T> deserializeAsList(String json, Class<T> listType) {
    try {
      return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, listType));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public List<Map<String, Object>> deserializeAsGenericList(String json) {
    try {
      if (json == null || json.isEmpty()) {
        return List.of();
      }
      return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Map<String, Object> deserializeAsGenericMap(String json) {
    try {
      if (json == null || json.isEmpty()) {
        return Map.of();
      }
      return mapper.readValue(json, mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public ObjectNode getAsJsonObject(Object object) {
    return mapper.valueToTree(object);
  }

  @Override
  public String getId(ObjectNode jsonObject) {
    return getAsString(jsonObject, "_id");
  }

  @Override
  public String getRev(ObjectNode jsonObject) {
    return getAsString(jsonObject, "_rev");
  }

  @Override
  public String getAsString(ObjectNode jsonObject, String key) {
    JsonNode node = jsonObject.get(key);
    return (node == null || node.isNull()) ? null : node.asText();
  }

  @Override
  public long getAsLong(ObjectNode jsonObject, String key) {
    JsonNode node = jsonObject.get(key);
    return (node == null || node.isNull()) ? 0L : node.asLong();
  }

  @Override
  public int getAsInt(ObjectNode jsonObject, String key) {
    JsonNode node = jsonObject.get(key);
    return (node == null || node.isNull()) ? 0 : node.asInt();
  }

  @Override
  public JsonNode getKeyFromObject(JsonNode jsonElement, String key) {
    if (jsonElement == null || key == null) return null;
    JsonNode obj = jsonElement;
    if (!jsonElement.isObject()) {
      if (jsonElement.isContainerNode() && jsonElement.has(key)) {
        return jsonElement.get(key);
      }
      return null;
    }
    return obj.get(key);
  }

  @Override
  public String getKeyFromObject(Reader reader, String key) {
    try {
      ObjectNode obj = (ObjectNode) mapper.readTree(reader);
      JsonNode node = obj.get(key);
      return (node == null || node.isNull()) ? null : node.asText();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public <T> void extractDocsToList(Reader reader, Class<T> listType, List<T> list) {
    try {
      ObjectNode root = (ObjectNode) mapper.readTree(reader);
      ArrayNode docs = (ArrayNode) root.get("docs");
      if (docs != null) {
        for (JsonNode node : docs) {
          T obj = mapper.treeToValue(node, listType);
          list.add(obj);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public <T> void extractRowToList(Reader reader, Class<T> listType, List<T> list, Boolean includeDocs) {
    try {
      ObjectNode root = (ObjectNode) mapper.readTree(reader);
      ArrayNode rows = (ArrayNode) root.get("rows");
      if (rows != null) {
        for (JsonNode row : rows) {
          JsonNode node = row;
          if (Boolean.TRUE.equals(includeDocs) && row.has("doc")) {
            node = row.get("doc");
          }
          T obj = mapper.treeToValue(node, listType);
          list.add(obj);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public <T> T getQueryValue(Reader reader, Class<T> type) {
    try {
      ObjectNode root = (ObjectNode) mapper.readTree(reader);
      ArrayNode rows = (ArrayNode) root.get("rows");
      if (rows == null || rows.size() != 1) {
        throw new org.lightcouch.NoDocumentException("Expecting a single result but was: " + (rows == null ? 0 : rows.size()));
      }
      JsonNode valueNode = rows.get(0).get("value");
      return mapper.treeToValue(valueNode, type);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public <K, V, T> ViewResult<K, V, T> handleViewResult(Reader reader, Boolean includeDocs, Class<K> classOfK, Class<V> classOfV, Class<T> classOfT) {
    try {
      ObjectNode json = (ObjectNode) mapper.readTree(reader);
      ViewResult<K, V, T> vr = new ViewResult<>();
      vr.setTotalRows(getAsLong(json, "total_rows"));
      vr.setOffset(getAsInt(json, "offset"));
      vr.setUpdateSeq(getAsString(json, "update_seq"));
      ArrayNode jsonArray = (ArrayNode) json.get("rows");
      if (jsonArray != null) {
        for (JsonNode e : jsonArray) {
          ViewResult<K, V, T>.Rows row = vr.new Rows();
          row.setId(mapper.treeToValue(e.get("id"), String.class));
          if (classOfK != null) {
            row.setKey(mapper.treeToValue(e.get("key"), classOfK));
          }
          if (classOfV != null) {
            row.setValue(mapper.treeToValue(e.get("value"), classOfV));
          }
          if (Boolean.TRUE.equals(includeDocs) && e.has("doc")) {
            row.setDoc(mapper.treeToValue(e.get("doc"), classOfT));
          }
          vr.getRows().add(row);
        }
      }
      return vr;
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

}
