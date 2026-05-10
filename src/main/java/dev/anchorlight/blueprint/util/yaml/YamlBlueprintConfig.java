package dev.anchorlight.blueprint.util.yaml;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base YAML configuration wrapper.
 */
public class YamlBlueprintConfig {
    static final Yaml yaml = new Yaml();
    private Map<String, Object> data;

    public YamlBlueprintConfig(Map<String, Object> data) {
        this.data = data != null ? data : new LinkedHashMap<>();
    }

    public YamlBlueprintConfig() {
        this.data = new LinkedHashMap<>();
    }

    @Override
    public String toString() {
        return yaml.dump(this.data);
    }

    public void save(File file) {
        try (FileWriter writer = new FileWriter(file)) {
            yaml.dump(this.data, writer);
        } catch (IOException e) {
            throw new YamlBlueprintConfigException("Unable to write to file", e);
        }
    }

    public Map<String, Object> getData() {
        return data;
    }

    public boolean absent(String key) {
        return !exists(key);
    }

    public boolean contains(String key) {
        return exists(key);
    }

    public boolean exists(String key) {
        return this.data.containsKey(key);
    }

    public void set(Map<String, ?> values) {
        this.data.putAll(values);
    }

    public void set(String key, Object value) {
        this.data.put(key, value);
    }

    public List<String> getStringList(String key) {
        return getList(key, String.class);
    }

    public List<Integer> getIntList(String key) {
        return getList(key, Integer.class);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getList(String key, Class<T> cast) {
        Object obj = data.get(key);
        if (!(obj instanceof List)) {
            return new ArrayList<>();
        }
        List<?> list = (List<?>) obj;
        List<T> result = new ArrayList<>();
        for (Object object : list) {
            if (object != null && !cast.isAssignableFrom(object.getClass())) {
                throw new ClassCastException("List element '" + object + "' of type " +
                        object.getClass() + " cannot be casted to " + cast);
            }
            result.add((T) object);
        }
        return result;
    }

    public int getInt(String key) {
        Number val = get(key, Number.class);
        return val != null ? val.intValue() : 0;
    }

    public int getInt(String key, int def) {
        return contains(key) ? getInt(key) : def;
    }

    public long getLong(String key) {
        Number val = get(key, Number.class);
        return val != null ? val.longValue() : 0L;
    }

    public long getLong(String key, long def) {
        return contains(key) ? getLong(key) : def;
    }

    public double getDouble(String key) {
        Number val = get(key, Number.class);
        return val != null ? val.doubleValue() : 0.0;
    }

    public double getDouble(String key, double def) {
        return contains(key) ? getDouble(key) : def;
    }

    public String getString(String key) {
        return get(key, String.class);
    }

    public String getString(String key, String def) {
        return contains(key) ? getString(key) : def;
    }

    public String getAsString(String key) {
        Object val = get(key);
        return val != null ? val.toString() : null;
    }

    public String getAsString(String key, String def) {
        return contains(key) ? getAsString(key) : def;
    }

    public boolean getBoolean(String key) {
        Boolean val = get(key, Boolean.class);
        return val != null && val;
    }

    public boolean getBoolean(String key, boolean def) {
        return contains(key) ? getBoolean(key) : def;
    }

    public Object get(String key) {
        return get(key, Object.class);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> cast) {
        Object value = this.data.get(key);
        if (value == null) return null;

        if (cast.isAssignableFrom(value.getClass())) {
            return (T) value;
        }

        // Handle cases where number types might not match exactly (e.g. Integer vs Long)
        if (value instanceof Number && Number.class.isAssignableFrom(cast)) {
            return (T) value;
        }

        return null;
    }
}
