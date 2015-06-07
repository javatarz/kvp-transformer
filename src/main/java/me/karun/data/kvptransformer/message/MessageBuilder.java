package me.karun.data.kvptransformer.message;

import com.google.common.collect.Lists;
import javafx.util.Pair;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collector;

public class MessageBuilder {

  private final List<Pair<String, Object>> data;
  private final Function<String, List<String>> splitKeys = key -> Arrays.asList(key.split("\\."));
  private final Function<List<String>, List<String>> reverseList = Lists::reverse;
  private final BinaryOperator<Stack<String>> stackCombiner = (s1, s2) -> {
    final Stack<String> freshStack = new Stack<>();
    s1.forEach(freshStack::push);
    s2.forEach(freshStack::push);
    return freshStack;
  };
  private final Function<List<String>, Stack<String>> toStack = l -> l.stream().collect(Collector.of(Stack::new, Stack::push, stackCombiner));

  public MessageBuilder() {
    this.data = new ArrayList<>();
  }

  public MessageKey withKey(final String key) {
    return new MessageKey(this, key);
  }

  public MessageBuilder insert(final String key, final Object value) {
    data.add(new Pair<>(key, value));
    return this;
  }

  public Message build() {
    final Map<String, Object> dataTree = new HashMap<>();
    data.stream()
      .map(pair -> new Pair<>(
        splitKeys.andThen(reverseList).andThen(toStack).apply(pair.getKey()),
        pair.getValue()))
      .forEach(p -> insertInMap(dataTree, p.getKey(), p.getValue()));

    return new Message(dataTree);
  }

  private MessageBuilder insertInMap(final Map<String, Object> treeSubset, final Stack<String> keyStack, final Object value) {
    final String keyElement = keyStack.pop();
    final Optional<Object> element = Optional.ofNullable(treeSubset.get(keyElement));

    if (element.isPresent()) {
      return processElementBeingPresent(keyStack, element.get(), value);
    }

    return processElementAbsent(treeSubset, keyElement, keyStack, value);
  }

  private MessageBuilder processElementBeingPresent(final Stack<String> keyStack, final Object o, final Object value) {
    if (keyStack.isEmpty()) {
      throw new RuntimeException("Element already present at location");
    }

    if (o instanceof Map) {
      return insertInMap((Map<String, Object>) o, keyStack, value);
    }

    throw new RuntimeException("Leaf exists at specified location");
  }

  private MessageBuilder processElementAbsent(final Map<String, Object> treeSubset, final String keyElement,
                                              final Stack<String> keyStack, final Object value) {
    final Map<String, Object> node = new HashMap<>();
    treeSubset.put(keyElement, node);

    if (keyStack.isEmpty()) {
      treeSubset.put(keyElement, value);
      return this;
    }

    return insertInMap(node, keyStack, value);
  }
}