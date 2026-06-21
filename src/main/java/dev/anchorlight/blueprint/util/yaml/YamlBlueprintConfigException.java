package dev.anchorlight.blueprint.util.yaml;

/**
 * Runtime exception for Blueprint YAML configuration errors.
 */
public class YamlBlueprintConfigException extends RuntimeException {
    public YamlBlueprintConfigException(String message) {
        super(message);
    }

    public YamlBlueprintConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
