/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yourorg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.cleanup.ModifierOrder;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;


public class StaticizeNonOverridableMethods extends Recipe {

  @Override
  public String getDisplayName() {
    return "Staticize non-overridable methods";
  }

  @Override
  public String getDescription() {
    return "Add `static` modifiers to non-overridable methods (private or final) that don’t access instance data.";
  }

  @Override
  public JavaIsoVisitor<ExecutionContext> getVisitor() {
    // list of non-overridable instance methods that can be static
    final List<J.MethodDeclaration> staticInstanceMethods = new ArrayList<>();

    return new JavaIsoVisitor<ExecutionContext>() {
      @Override
      public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        // Skip nested class (inner class or static nested class)
        boolean isNestedClass = classDecl.getType() != null && classDecl.getType().getOwningClass() != null;
        if (isNestedClass) {
          return classDecl;
        }

        Graph<String, J> graph = BuildInstanceDataAccessGraph.build(classDecl);

        staticInstanceMethods.addAll(findStaticMethods(graph));
        return super.visitClassDeclaration(classDecl, ctx);
      }

      @Override
      public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        boolean canBeStatic = staticInstanceMethods.stream()
            .anyMatch(m -> method.getSimpleName().equals(m.getSimpleName())
                && method.getParameters().equals(m.getParameters()));
        return canBeStatic ? addStaticModifier(method) : method;
      }
    };
  }

  private List<J.MethodDeclaration> findStaticMethods(Graph<String, J> graph) {
    Set<J.MethodDeclaration> instanceAccessedMethods = new HashSet<>();

    // a queue to store instance data accessed elements, an element can be either instance variable or overridable instance method.
    Queue<Graph<String, J>.Node> accessedElements = graph.getNodesMap().values()
        .stream()
        .filter(node -> isInstanceDataAccessed(node.element))
        .collect(Collectors.toCollection(LinkedList::new));
    Set<String> visitedNodeIds = new HashSet<>();

    // propagate instance data accessed methods in graph, so the rest methods are not instance data accessed and can be static.
    while (!accessedElements.isEmpty()) {
      Graph<String, J>.Node node = accessedElements.poll();
      if (visitedNodeIds.contains(node.getId())) {
        continue;
      }
      visitedNodeIds.add(node.getId());

      if (node.getElement() instanceof J.MethodDeclaration) {
        instanceAccessedMethods.add((J.MethodDeclaration) node.getElement() );
      }

      if (!node.getLinks().isEmpty()) {
        accessedElements.addAll(node.getLinks());
      }
    }

    return graph.getNodesMap()
        .values()
        .stream()
        .map(Graph.Node::getElement)
        .filter(element -> element instanceof J.MethodDeclaration)
        .map(J.MethodDeclaration.class::cast)
        .filter(m -> !instanceAccessedMethods.contains(m))
        .collect(Collectors.toList());
  }

  // Any element (method or variable) accessed instance variable or overridable methods(public, protected, or package-private) can not be static.
  private boolean isInstanceDataAccessed(J element) {
    if (element instanceof J.Identifier) {
      // it's an instance variable
      return true;
    }

    if (element instanceof J.MethodDeclaration) {
      return !isNonOverridableMethod((J.MethodDeclaration) element);
    }

    // a base class method has a null element.
    if (element == null) {
      return true;
    }
    return false;
  }

  /**
   * Visitor to build instance data access graph in a class.
   * The graph contains two types of elements:
   *  1. instance variables
   *  2. instance methods (overridable or non-overridable)
   * <p>
   *  e.g. If method `m_a` calls method `m_b` and variable `v`, in the graph, there will be two links in the graph:
   *  `m_b` -> `m_a` and `v` -> `m_a`;
   */
  @EqualsAndHashCode(callSuper = true)
  private static class BuildInstanceDataAccessGraph extends JavaIsoVisitor<Graph<String, J>> {
    private final List<J.MethodDeclaration> instanceMethods;
    private final List<J.VariableDeclarations.NamedVariable> instanceVariables;
    private final Graph<String, J> instanceDataAccessGraph;

    private BuildInstanceDataAccessGraph() {
      instanceMethods = new ArrayList<>();
      instanceVariables = new ArrayList<>();
      instanceDataAccessGraph = new Graph<>();
    }

    static Graph<String, J> build(J j) {
      BuildInstanceDataAccessGraph visitor = new BuildInstanceDataAccessGraph();
      return visitor.reduce(j, visitor.instanceDataAccessGraph);
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, Graph<String, J> graph) {
      instanceMethods.addAll(getInstanceMethods(classDecl));
      instanceVariables.addAll(getInstanceVariables(classDecl));
      instanceMethods.forEach(m -> graph.addNode(buildInstanceMethodId(m), m));
      instanceVariables.forEach(v -> graph.addNode(buildInstanceVariableId(v.getName()), v.getName()));
      return super.visitClassDeclaration(classDecl, graph);
    }

    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, Graph<String, J> graph) {
      J.MethodDeclaration m = super.visitMethodDeclaration(method, graph);

      // skip class methods
      if (m.hasModifier(J.Modifier.Type.Static)) {
        return m;
      }

      AddInstanceDataAccessGraphLinks.process(getCursor().getValue(),
          method,
          instanceMethods,
          instanceVariables,
          instanceDataAccessGraph);
      return m;
    }
  }

  /**
   * Visitor to add links in a method to the graph.
   */
  @EqualsAndHashCode(callSuper = true)
  private static class AddInstanceDataAccessGraphLinks extends JavaIsoVisitor<Graph<String, J>> {
    private final J.MethodDeclaration thisMethod;
    private final List<J.MethodDeclaration> instanceMethods;
    private final List<J.VariableDeclarations.NamedVariable> instanceVariables;

    private AddInstanceDataAccessGraphLinks(J.MethodDeclaration thisMethod,
        List<J.MethodDeclaration> instanceMethods,
        List<J.VariableDeclarations.NamedVariable> instanceVariables
    ) {
      this.thisMethod = thisMethod;
      this.instanceMethods = instanceMethods;
      this.instanceVariables = instanceVariables;
    }

    /**
     * Add instance data access links to the graph.
     * @param j subtree to traverse, points to a method.
     * @param graph graph to be updated by adding links (present instance data access) in this method
     */
    static void process(J j,
        J.MethodDeclaration thisMethod,
        List<J.MethodDeclaration> instanceMethods,
        List<J.VariableDeclarations.NamedVariable> instanceVariables,
        Graph<String, J> graph
    ) {
      new AddInstanceDataAccessGraphLinks(thisMethod, instanceMethods, instanceVariables)
          .reduce(j, graph);
    }

    @Override
    public J.Identifier visitIdentifier(J.Identifier identifier, Graph<String, J> graph) {
      J.Identifier id = super.visitIdentifier(identifier, graph);

      // instance method calls will be handled by `visitMethodInvocation`, handles instance variables only here.
      boolean isNotVariable = id.getType() == null || id.getFieldType() == null;
      if (isNotVariable) {
        return id;
      }

      boolean isInstanceVariable = instanceVariables.stream()
          .anyMatch(v -> id.getFieldType().equals(v.getName().getFieldType())
              && id.getType().equals(v.getName().getType())
              && id.getSimpleName().equals(v.getSimpleName()));

      // the variable may be a base class instance variable
      if (!isInstanceVariable
          && id.getFieldType() != null
          && id.getFieldType().getOwner() instanceof JavaType.Class
          && !id.getFieldType().getFlags().contains(Flag.Static)) {
        isInstanceVariable = true;
      }

      if (isInstanceVariable) {
        graph.addLink(buildInstanceVariableId(id),
            identifier,
            buildInstanceMethodId(thisMethod),
            thisMethod);
      }

      return id;
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Graph<String, J> graph) {
      J.MethodInvocation m = super.visitMethodInvocation(method, graph);

      // skip recursive
      if (method.getSimpleName().equals(thisMethod.getSimpleName())) {
        return m;
      }

      boolean isInstanceMethod = instanceMethods.stream()
          .anyMatch(im -> im.getSimpleName().equals(method.getSimpleName()));
      boolean isBaseClassMethod = false;

      // the method may be a base class instance method
      if (!isInstanceMethod
          && method.getMethodType() != null
          && method.getMethodType().getDeclaringType() instanceof JavaType.Class
          && !method.getMethodType().getFlags().contains(Flag.Static)) {
        isBaseClassMethod = true;
      }

      if (isInstanceMethod || isBaseClassMethod) {
        J.MethodDeclaration invokedMethod = null;
        if (isInstanceMethod) {
          invokedMethod = instanceMethods.stream()
              .filter(im -> im.getSimpleName().equals(method.getSimpleName()))
              .findFirst()
              .get();
        }

        graph.addLink(buildInstanceMethodId(invokedMethod),
            invokedMethod,
            buildInstanceMethodId(thisMethod),
            thisMethod
        );
      }

      return m;
    }
  }

  private static J.MethodDeclaration addStaticModifier(J.MethodDeclaration m) {
    List<J.Modifier> modifiers = m.getModifiers();

    // it expects the input method is non-overridable, so it should has at least one modifier(`private` or `final`)
    if (modifiers.isEmpty()) {
      return m;
    }

    boolean hasFinalModifier = modifiers.stream()
        .anyMatch(mod -> mod.getType() == J.Modifier.Type.Final);

    if (hasFinalModifier) {
      // replace `final` with `static`, since it's redundant.
      modifiers = ListUtils.map(m.getModifiers(),
          (i, mod) -> mod.getType() == J.Modifier.Type.Final ? mod.withType(J.Modifier.Type.Static) : mod);
    } else {
      // add `static` modifier
      modifiers = ListUtils.concat(modifiers, buildStaticModifier(modifiers));
    }

    return m.withModifiers(ModifierOrder.sortModifiers(modifiers));
  }

  private static J.Modifier buildStaticModifier(List<J.Modifier> ms) {
    // ms is guaranteed contains `private` or `final`.
    Space singleSpace = Space.build(" ", Collections.emptyList());
    return ms.stream()
        .filter(mod -> mod.getType() == J.Modifier.Type.Private || mod.getType() == J.Modifier.Type.Final)
        .findFirst()
        .get()
        .withId(Tree.randomId())
        .withPrefix(singleSpace)
        .withType(J.Modifier.Type.Static);
  }

  private static List<J.MethodDeclaration> getInstanceMethods(J.ClassDeclaration classDecl) {
    return classDecl.getBody()
        .getStatements()
        .stream()
        .filter(statement -> statement instanceof J.MethodDeclaration)
        .map(J.MethodDeclaration.class::cast)
        .filter(m -> !m.hasModifier(J.Modifier.Type.Static))
        .collect(Collectors.toList());
  }

  private static List<J.VariableDeclarations.NamedVariable> getInstanceVariables(J.ClassDeclaration classDecl) {
    return classDecl.getBody()
        .getStatements()
        .stream()
        .filter(statement -> statement instanceof J.VariableDeclarations)
        .map(J.VariableDeclarations.class::cast)
        .filter(mv -> !mv.hasModifier(J.Modifier.Type.Static))
        .map(J.VariableDeclarations::getVariables)
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  private static String buildInstanceVariableId(J.Identifier v) {
    return "V_" + v.getSimpleName() + "_" + v.getFieldType();
  }

  private static String buildInstanceMethodId(J.MethodDeclaration m) {
    if (m != null) {
      String methodName = m.getSimpleName();
      StringBuilder params = new StringBuilder();
      m.getParameters().forEach(statement -> params.append(statement));
      return "M_" + methodName + "_" + params.toString();
    } else {
      return "M_anyBaseClassMethod";
    }
  }

  private static boolean isNonOverridableMethod(J.MethodDeclaration m) {
    return m.hasModifier(J.Modifier.Type.Private) || m.hasModifier(J.Modifier.Type.Final);
  }
}
