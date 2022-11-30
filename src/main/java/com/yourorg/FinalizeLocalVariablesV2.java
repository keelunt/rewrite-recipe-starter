/*
 * Copyright 2020 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.Collections;

@Incubating(since = "7.0.0")
public class FinalizeLocalVariablesV2 extends Recipe {

  @Override
  public String getDisplayName() {
    return "Finalize local variables";
  }

  @Override
  public String getDescription() {
    return "Adds the `final` modifier keyword to local variables which are not reassigned.";
  }

  @Override
  public JavaIsoVisitor<ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<ExecutionContext>() {
      // A map to store local variables' assignment info,
      // Key: MethodDeclaration, Value: a map keyed by local variable, valued by assignment count.
      final Map<J.MethodDeclaration, Map<J.Identifier, Integer>> methodLocalVariablesAssignmentCountMap = new HashMap<>();

      @Override
      public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        // Find all local variables and assignment counts.
        Map<J.Identifier, Integer> variableCountMap =
            CollectLocalVariablesAndAssignmentCounts.collect(getCursor().getValue());
        methodLocalVariablesAssignmentCountMap.put(method, variableCountMap);
        return super.visitMethodDeclaration(method, ctx);
      }

      @Override
      public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
          ExecutionContext ctx
      ) {
        J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, ctx);

        // if this already has "final", we don't need to bother going any further; we're done
        if (mv.hasModifier(J.Modifier.Type.Final)) {
          return mv;
        }

        // skip for loop control
        if (isInForLoopControl(getCursor())) {
          return multiVariable;
        }

        // find current method
        J.MethodDeclaration m = findCurrentMethod(getCursor());
        if (m == null) {
          // not a local variable
          return mv;
        }

        Map<J.Identifier, Integer> vcMap = methodLocalVariablesAssignmentCountMap.get(m);
        boolean areAllAssignedOnce = mv.getVariables()
            .stream()
            .allMatch(v -> vcMap.containsKey(v.getName()) && vcMap.get(v.getName()) == 1);

        if (areAllAssignedOnce) {
          mv = autoFormat(mv.withModifiers(ListUtils.concat(mv.getModifiers(),
              new J.Modifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, J.Modifier.Type.Final,
                  Collections.emptyList()))), ctx);
        }

        return mv;
      }
    };
  }

  private static boolean isInForLoopControl(Cursor cursor) {
    return cursor.getParentTreeCursor()
        .getValue() instanceof J.ForLoop.Control;
  }

  private static boolean isInForLoop(Cursor cursor) {
    return cursor.dropParentUntil(parent -> parent instanceof J.MethodDeclaration || parent instanceof J.ForLoop)
        .getValue() instanceof J.ForLoop;
  }

  private static boolean isInDoWhileLoopLoop(Cursor cursor) {
    return cursor.dropParentUntil(parent -> parent instanceof J.MethodDeclaration || parent instanceof J.DoWhileLoop)
        .getValue() instanceof J.DoWhileLoop;
  }

  private static boolean isInWhileLoop(Cursor cursor) {
    return cursor.dropParentUntil(parent -> parent instanceof J.MethodDeclaration || parent instanceof J.WhileLoop)
        .getValue() instanceof J.WhileLoop;
  }

  private static boolean isMethodParameter(Cursor cursor) {
    return cursor.dropParentUntil(J.class::isInstance)
        .getValue() instanceof J.MethodDeclaration;
  }

  @Nullable
  private J.MethodDeclaration findCurrentMethod(Cursor cursor) {
    Cursor c =
        cursor.dropParentUntil(parent -> parent instanceof J.ClassDeclaration || parent instanceof J.MethodDeclaration);
    if (c.getValue() instanceof J.MethodDeclaration) {
      return (J.MethodDeclaration) c.getValue();
    } else {
      return null;
    }
  }

  @EqualsAndHashCode(callSuper = true)
  private static class CollectLocalVariablesAndAssignmentCounts extends JavaIsoVisitor<Map<J.Identifier, Integer>> {
    /**
     * @param j        The subtree to search. class method level.
     * @return A map of local variables and assignment counts to the variable.
     */
    static Map<J.Identifier, Integer> collect(J j) {
      return new CollectLocalVariablesAndAssignmentCounts().reduce(j, new HashMap<>());
    }

    @Override
    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
        Map<J.Identifier, Integer> variableCountMap) {
      J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, variableCountMap);

      // skip method parameters
      if (isMethodParameter(getCursor())) {
        return mv;
      }

      // skip for loop control
      if (isInForLoopControl(getCursor())) {
        return mv;
      }

      multiVariable.getVariables()
          .forEach(v -> variableCountMap.put(v.getName(), (v.getInitializer() != null) ? 1 : 0));

      return multiVariable;
    }

    @Override
    public J.Assignment visitAssignment(J.Assignment assignment, Map<J.Identifier, Integer> variableCountMap) {
      J.Assignment a = super.visitAssignment(assignment, variableCountMap);
      updateAssignmentCount(getCursor(), a.getVariable(), variableCountMap);
      return a;
    }

    @Override
    public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp,
        Map<J.Identifier, Integer> variableCountMap
    ) {
      J.AssignmentOperation a = super.visitAssignmentOperation(assignOp, variableCountMap);
      updateAssignmentCount(getCursor(), a.getVariable(), variableCountMap);
      return a;
    }

    @Override
    public J.Unary visitUnary(J.Unary unary, Map<J.Identifier, Integer> variableCountMap) {
      J.Unary u = super.visitUnary(unary, variableCountMap);
      updateAssignmentCount(getCursor(), u.getExpression(), variableCountMap);
      return u;
    }

    private static void updateAssignmentCount(Cursor cursor, Expression expression,
        Map<J.Identifier, Integer> variableCountMap
    ) {
      if (expression instanceof J.Identifier) {
        J.Identifier i = (J.Identifier) expression;

        int increment = 1;
        if (isInWhileLoop(cursor) || isInForLoop(cursor) || isInDoWhileLoopLoop(cursor)) {
          increment = 2;
        }

        // UUID is different, so need to do name and type match
        Optional<J.Identifier> maybeId = variableCountMap.keySet().stream().filter(id -> id.getSimpleName().equals(i.getSimpleName())
            && id.getFieldType().equals(i.getFieldType())).findFirst();

        if (maybeId.isPresent()) {
          J.Identifier id = maybeId.get();
          int newCount = variableCountMap.get(id) + increment;
          variableCountMap.put(id, newCount);
        }
      }
    }
  }
}
