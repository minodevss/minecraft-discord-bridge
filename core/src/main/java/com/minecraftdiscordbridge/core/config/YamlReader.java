package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.config.YamlValue.Field;
import com.minecraftdiscordbridge.core.config.YamlValue.Mapping;
import com.minecraftdiscordbridge.core.config.YamlValue.Scalar;
import com.minecraftdiscordbridge.core.config.YamlValue.Sequence;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.exceptions.Mark;
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.schema.CoreSchema;

final class YamlReader {
    private static final int MAX_DEPTH = 32;
    private static final char BYTE_ORDER_MARK = 0xFEFF;
    private static final Location START = new Location(1, 1);
    private static final Mapping EMPTY = new Mapping(List.of(), START);

    private final Problems problems;

    private YamlReader(Problems problems) {
        this.problems = problems;
    }

    static Optional<Mapping> readDocument(String text, Problems problems) {
        String content = !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
        LoadSettings settings = LoadSettings.builder()
                .setSchema(new CoreSchema())
                .setParseComments(false)
                .build();
        Optional<Node> root;
        try {
            root = new Compose(settings).composeString(content);
        } catch (MarkedYamlEngineException invalid) {
            problems.error(location(invalid.getProblemMark()), "", "invalid YAML: " + syntaxProblem(invalid));
            return Optional.empty();
        } catch (YamlEngineException invalid) {
            problems.error(null, "", "invalid YAML: " + invalid.getMessage());
            return Optional.empty();
        }
        if (root.isEmpty()) {
            return Optional.of(EMPTY);
        }
        YamlValue value = new YamlReader(problems).convert(root.get(), "", 0);
        if (value instanceof Mapping mapping) {
            return Optional.of(mapping);
        }
        if (value instanceof Scalar scalar && scalar.isNull()) {
            return Optional.of(EMPTY);
        }
        if (value != null) {
            problems.error(value.at(), "", "expected settings written as key: value lines, got " + value.describe());
        }
        return Optional.empty();
    }

    private @Nullable YamlValue convert(Node node, String path, int depth) {
        Location at = location(node.getStartMark(), START);
        if (depth > MAX_DEPTH) {
            problems.error(at, path, "nested too deeply");
            return null;
        }
        if (node instanceof ScalarNode scalar) {
            return new Scalar(scalar.getValue(), kind(scalar.getTag()), at);
        }
        if (node instanceof SequenceNode sequence) {
            List<YamlValue> items = new ArrayList<>();
            List<Node> children = sequence.getValue();
            for (int index = 0; index < children.size(); index++) {
                YamlValue item = convert(children.get(index), path + "[" + index + "]", depth + 1);
                if (item != null) {
                    items.add(item);
                }
            }
            return new Sequence(items, at);
        }
        if (node instanceof MappingNode mapping) {
            return convertMapping(mapping, path, depth, at);
        }
        problems.error(at, path, "unsupported YAML node");
        return null;
    }

    private Mapping convertMapping(MappingNode mapping, String path, int depth, Location at) {
        List<Field> fields = new ArrayList<>();
        Map<String, Location> seen = new HashMap<>();
        for (NodeTuple tuple : mapping.getValue()) {
            Location keyAt = location(tuple.getKeyNode().getStartMark(), START);
            if (!(tuple.getKeyNode() instanceof ScalarNode keyNode)) {
                problems.error(keyAt, path, "keys must be plain text");
                continue;
            }
            String key = keyNode.getValue();
            String fieldPath = path.isEmpty() ? key : path + "." + key;
            Location first = seen.putIfAbsent(key, keyAt);
            if (first != null) {
                problems.error(keyAt, fieldPath, duplicate(first));
                continue;
            }
            YamlValue value = convert(tuple.getValueNode(), fieldPath, depth + 1);
            if (value != null) {
                fields.add(new Field(key, keyAt, value));
            }
        }
        return new Mapping(fields, at);
    }

    static String duplicate(Location first) {
        return "duplicate key; it is already set at line " + first.line() + ". Remove one of them.";
    }

    private static Scalar.Kind kind(Tag tag) {
        if (tag.equals(Tag.NULL)) {
            return Scalar.Kind.NULL;
        }
        if (tag.equals(Tag.BOOL)) {
            return Scalar.Kind.BOOLEAN;
        }
        if (tag.equals(Tag.INT)) {
            return Scalar.Kind.INTEGER;
        }
        if (tag.equals(Tag.FLOAT)) {
            return Scalar.Kind.FLOAT;
        }
        return Scalar.Kind.STRING;
    }

    private static String syntaxProblem(MarkedYamlEngineException invalid) {
        String context = invalid.getContext();
        String problem = invalid.getProblem();
        return context == null || context.isEmpty() ? problem : context + ", " + problem;
    }

    private static @Nullable Location location(Optional<Mark> mark) {
        return mark.map(found -> new Location(found.getLine() + 1, found.getColumn() + 1))
                .orElse(null);
    }

    private static Location location(Optional<Mark> mark, Location fallback) {
        Location found = location(mark);
        return found == null ? fallback : found;
    }
}
