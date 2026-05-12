package dev.anchorlight.blueprint.util.yaml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Map;

/**
 * YAML file configuration wrapper.
 */
public class YamlBlueprintConfigFile extends YamlBlueprintConfig {
    private final File file;

    @SuppressWarnings("unchecked")
    public YamlBlueprintConfigFile(File file) {
        this.file = file;
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> map = yaml.loadAs(reader, Map.class);
                if (map != null) {
                    set(map);
                }
            } catch (FileNotFoundException e) {
                throw new YamlBlueprintConfigException("Failed to read config", e);
            } catch (Exception e) {
                throw new YamlBlueprintConfigException("Error loading YAML", e);
            }
        }
    }

    public void save() {
        save(this.file);
    }
}
