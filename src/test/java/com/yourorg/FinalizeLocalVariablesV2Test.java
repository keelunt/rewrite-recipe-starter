package com.yourorg;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;


public class FinalizeLocalVariablesV2Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FinalizeLocalVariablesV2());
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1478")
    @Test
    void initializedInWhileLoop() {
        rewriteRun(java("""
                import java.io.BufferedReader;
                class T {
                    public void doSomething(StringBuilder sb, BufferedReader br) {
                        String line;
                        try {
                            while ((line = br.readLine()) != null) {
                                sb.append(line);
                            }
                        } catch (Exception e) {
                            error("Exception", e);
                        }
                    }
                    private static void error(String s, Exception e) {

                    }
                }
            """));
    }

    @Test
    void localVariablesAreMadeFinal() {
        rewriteRun(java("""
                class A {
                    public void test() {
                        int n = 1;
                        for(int i = 0; i < n; i++) {
                        }
                    }
                }
            """, """
                class A {
                    public void test() {
                        final int n = 1;
                        for(int i = 0; i < n; i++) {
                        }
                    }
                }
            """));
    }

    @Test
    void identifyReassignedLocalVariables() {
        rewriteRun(java("""
                class A {
                    public void test() {
                        int a = 0;
                        int b = 0;
                        int c = 10;
                        for(int i = 0; i < c; i++) {
                            a = i + c;
                            b++;
                        }
                    }
                }
            """, """
                class A {
                    public void test() {
                        int a = 0;
                        int b = 0;
                        final int c = 10;
                        for(int i = 0; i < c; i++) {
                            a = i + c;
                            b++;
                        }
                    }
                }
            """));
    }

    // @Disabled("consider uninitialized local variables non final")
    @Test
    void multipleVariablesDeclarationOnSingleLine() {
        rewriteRun(java("""
                    class A {
                        public void multiVariables() {
                            int a, b = 1;
                            a = 0;
                        }
                    }
                """,
            // the final only applies to any initialized variables (b in this case)
            """
                    class A {
                        public void multiVariables() {
                            final int a, b = 1;
                            a = 0;
                        }
                    }
                """));
    }

    // @Disabled("consider uninitialized local variables non final")
    @Test
    void calculateLocalVariablesInitializerOffset() {
        rewriteRun(java("""
                class A {
                    public void testOne() {
                        int a;
                        a = 0;
                        System.out.println(a);
                    }

                    public void testTwo() {
                        int a;
                        a = 0;
                        a = 0;
                        System.out.println(a);
                    }

                    public void testThree() {
                        int a;
                        a = 0;
                        a++;
                        System.out.println(a);
                    }
                }
            """, """
                class A {
                    public void testOne() {
                        final int a;
                        a = 0;
                        System.out.println(a);
                    }
                
                    public void testTwo() {
                        int a;
                        a = 0;
                        a = 0;
                        System.out.println(a);
                    }
                
                    public void testThree() {
                        int a;
                        a = 0;
                        a++;
                        System.out.println(a);
                    }
                }
            """));
    }

    @Test
    @Disabled
    void calculateLocalVariablesInitializerBranching() {
        rewriteRun(java("""
                class A {
                    public void test(boolean hasThing) {
                        int a;
                        if (hasThing) {
                            a = 0;
                        } else {
                            a = 1;
                        }
                        System.out.println(a);
                    }
                }
            """, """
                class A {
                    public void test(boolean hasThing) {
                        final int a;
                        if (hasThing) {
                            a = 0;
                        } else {
                            a = 1;
                        }
                        System.out.println(a);
                    }
                }
            """));
    }

    @Disabled("consider uninitialized local variables non final")
    @Test
    void forEachLoopAssignmentMadeFinal() {
        rewriteRun(java("""
                class Test {
                    public static void testForEach(String[] args) {
                        for (String a : args) {
                            System.out.println(a);
                        }

                        for (String b : args) {
                            b = b.toUpperCase();
                            System.out.println(b);
                        }
                    }
                }
            """, """
                class Test {
                    public static void testForEach(String[] args) {
                        for (final String a : args) {
                            System.out.println(a);
                        }
                
                        for (String b : args) {
                            b = b.toUpperCase();
                            System.out.println(b);
                        }
                    }
                }
            """));
    }

    @Test
    void localVariableScopeAwareness() {
        rewriteRun(java("""
                class Test {
                    public static void testA() {
                        int a = 0;
                        a = 1;
                    }

                    public static void testB() {
                        int a = 0;
                    }
                }
            """, """
                class Test {
                    public static void testA() {
                        int a = 0;
                        a = 1;
                    }
                
                    public static void testB() {
                        final int a = 0;
                    }
                }
            """));
    }

    @Test
    @Disabled("consider uninitialized local variables non final")
    void forEachLoopScopeAwareness() {
        rewriteRun(java("""
                class Test {
                    public static void testForEach(String[] args) {
                        for (String i : args) {
                            System.out.println(i);
                        }

                        for (String i : args) {
                            i = i.toUpperCase();
                            System.out.println(i);
                        }
                    }
                }
            """, """
                class Test {
                    public static void testForEach(String[] args) {
                        for (final String i : args) {
                            System.out.println(i);
                        }
                
                        for (String i : args) {
                            i = i.toUpperCase();
                            System.out.println(i);
                        }
                    }
                }
            """));
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/549")
    void catchBlocksIgnored() {
        rewriteRun(java("""
                import java.io.IOException;

                class Test {
                    static {
                        try {
                            throw new IOException();
                        } catch (RuntimeException | IOException e) {
                            System.out.println("oops");
                        }
                    }
                }
            """));
    }

    @Test
        // aka "non-static-fields"
    void instanceVariablesIgnored() {
        rewriteRun(java("""
                class Test {
                    int instanceVariableUninitialized;
                    int instanceVariableInitialized = 0;
                }
            """));
    }

    @Test
        // aka "static fields"
    void classVariablesIgnored() {
        rewriteRun(java("""
                class Test {
                    static int classVariableInitialized = 0;
                }
            """));
    }

    @Test
    void classInitializersIgnored() {
        rewriteRun(java("""
                class Test {
                    static {
                        int n = 1;
                        for(int i = 0; i < n; i++) {
                        }
                    }
                }
            """));
    }

    @Test
    void methodParameterVariablesIgnored() {
        rewriteRun(java("""
                class Test {
                    private static int testMath(int x, int y) {
                        y = y + y;
                        return x + y;
                    }

                    public static void main(String[] args) {
                    }
                }
            """));
    }

    @Test
    void lambdaVariablesIgnored() {
        rewriteRun(java("""
                import java.util.stream.Stream;
                class A {
                    public boolean hasFoo(Stream<String> input) {
                        return input.anyMatch(word -> word.equalsIgnoreCase("foo"));
                    }
                }
            """));
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1357")
    @Test
    void forLoopVariablesIgnored() {
        rewriteRun(java("""
                import java.util.concurrent.FutureTask;

                class A {
                    void f() {
                        for(FutureTask<?> future; (future = new FutureTask<>(() -> "hello world")) != null;) { }
                    }
                }
            """));
    }

    @Test
    void reassignInLoopBody() {
        rewriteRun(java("""
                class B {
                  public int whileLoopTest(int a) {
                    int sum;
                    while (true) {
                      sum = a + 1;
                      a++;
                      if (a > 0) {
                        break;
                      }
                    }
                    return sum;
                  }

                  public int doWhileLoopTest(int a) {
                    int sum;
                    do {
                      sum = a + 1;
                      a++;
                      if (a > 0) {
                        break;
                      }
                    } while(true);
                    return sum;
                  }
                
                  public int forLoopTest(int n) {
                    int sum;
                    for (int i = 0; i < n; i++) {
                      sum = i + 1;
                    }
                    return n;
                  }
                }
            """));
    }

    @Disabled("indentation in the For loop is not correct somehow, to be fixed")
    @Test
    void assignmentInForLoopBody() {
        rewriteRun(java("""
                class B {
                  public int forLoopTest(int n) {
                    int sum;
                    for (int i = 0; i < n; i++) {
                      int a = 2;
                      sum = i + a;
                    }
                    return n;
                  }
                }
            """, """
                class B {
                  public int forLoopTest(int n) {
                    int sum;
                    for (int i = 0; i < n; i++) {
                      final int a = 2;
                      sum = i + a;
                    }
                    return n;
                  }
                }
            """));
    }
}
