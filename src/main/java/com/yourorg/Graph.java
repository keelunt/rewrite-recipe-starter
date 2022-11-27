package com.yourorg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.With;
import org.openrewrite.java.tree.J;


/**
 * Unweighted graph to present connectivity. (e.g methods invocations, variable assignments)
 * @param <T> node element
 * @param <ID> node unique ID
 */
public class Graph <ID, T extends J> {
  @Getter
  Map<ID, Node> nodesMap;

  public Graph() {
    nodesMap = new HashMap<>();
  }

  final class Node {
    // unique ID to present a node in graph
    @Getter
    ID id;

    @Getter
    T element;

    @Getter
    List<Node> links;

    @Getter
    int inDegree;

    @Getter
    @Setter
    int reachedCount;

    public Node(ID id, T element) {
      this.id = id;
      this.element = element;
      links = new ArrayList<>();
      inDegree = 0;
      reachedCount = 0;
    }
  }

  /**
   * Add a node to graph
   */
  public void addNode(ID id, T element) {
    if (!nodesMap.containsKey(id)) {
      nodesMap.put(id, new Node(id, element));
    }
  }

  /**
   * add a link to graph
   * @param from from-node id
   * @param to to-node id
   */
  public void addLink(ID from, T fromElement, ID to, T toElement) {
    if (!nodesMap.containsKey(from)) {
      Node fromNode = new Node(from, fromElement);
      nodesMap.put(from, fromNode);
    }

    if (!nodesMap.containsKey(to)) {
      Node toNode = new Node(to, toElement);
      nodesMap.put(to, toNode);
    }

    nodesMap.get(from).links.add(nodesMap.get(to));
    nodesMap.get(to).inDegree++;
  }

  public void resetReachedCount() {
    nodesMap.values().forEach(node -> {
      node.setReachedCount(0);
    });
  }

  public String print() {
    StringBuilder builder = new StringBuilder();
    builder.append("---Graph---\n");
    nodesMap.values().forEach(node -> {
      builder.append("  Node: ").append(node.id).append(" inDegree=").append(node.inDegree).append("\n");
      for (Node toNode : node.links) {
        builder.append("    \\-> to Node: ").append(toNode.id).append("\n");
      }
    });
    builder.append("-----------\n");
    return builder.toString();
  }
}
