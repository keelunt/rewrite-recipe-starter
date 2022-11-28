package com.yourorg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.Getter;
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

  @Data
  final class Node {
    // unique ID to present a node in graph
    ID id;

    T element;

    List<Node> links;

    int inDegree;

    public Node(ID id, T element) {
      this.id = id;
      this.element = element;
      links = new ArrayList<>();
      inDegree = 0;
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
    addNode(from, fromElement);
    addNode(to, toElement);
    nodesMap.get(from).links.add(nodesMap.get(to));
    nodesMap.get(to).inDegree++;
  }

  public String print() {
    StringBuilder builder = new StringBuilder();
    builder.append("---Graph---\n");
    nodesMap.values().forEach(node -> {
      builder.append("  Node: ").append(node.id).append(" (inDegree=").append(node.inDegree).append(")\n");
      for (Node toNode : node.links) {
        builder.append("    \\-> to Node: ").append(toNode.id).append("\n");
      }
    });
    builder.append("-----------\n");
    return builder.toString();
  }
}
