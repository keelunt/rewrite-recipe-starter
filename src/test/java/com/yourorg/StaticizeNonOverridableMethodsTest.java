package com.yourorg;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;

class StaticizeNonOverridableMethodsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new StaticizeNonOverridableMethods());
    }

    @Test
    void instanceVariableAccessed() {
        rewriteRun(
            java("""
                        class A {
                          private int a;
                        
                          private int func1() {
                            return a;
                          }
                          
                          final int func2() {
                            return a * 2;
                          }
                          
                          private final int func3() {
                            return a * 3;
                          }
                        }
                    """
            )
        );
    }

    @Test
    void instanceVariableNotAccessed() {
        rewriteRun(
            java("""
                        class A {
                          private int a;
                          
                          private int func1(int b) {
                            int c = 2;
                            return c + b;
                          }
                          
                          final int func2(int b) {
                            int c = 2;
                            return c - b;
                          }
                          
                          private final int func3(int b) {
                            int c = 2;
                            return c * b;
                          }
                        }
                    """,
                """
                        class A {
                          private int a;
                          
                          private static int func1(int b) {
                            int c = 2;
                            return c + b;
                          }
                          
                          static int func2(int b) {
                            int c = 2;
                            return c - b;
                          }
                          
                          private static int func3(int b) {
                            int c = 2;
                            return c * b;
                          }
                        }
                    """
            )
        );
    }

    @Test
    void overridableMethodsIgnored() {
        rewriteRun(
            java("""
                        class A {
                          public int func1(int a, int b) {
                            return a + b;
                          }
                        
                          protected int func2(int a, int b) {
                            return a - b;
                          }
                        
                          int func3(int a, int b) {
                            return a * b;
                          }
                        }
                    """
            )
        );
    }

    @Test
    void instanceMethodAccessed() {
        rewriteRun(
            java("""
                        class A {
                          int a = 1;
                        
                          private int fun1() {
                            return a;
                          }
                          
                          private int fun2(int b) {
                            return fun1() + b;
                          }
                          
                          final int func3(int b) {
                            return fun1() - b;
                          }
                          
                          private final int func4(int b) {
                            return fun1() * b;
                          }
                        }
                    """
            )
        );
    }

    @Test
    void classVariableAccessed() {
        rewriteRun(
            java("""
                        class A {
                          private static int a = 1;
                        
                          private int func1() {
                            return a;
                          }
                          
                          final int func2() {
                            return a + 1;
                          }
                          
                          private final int func3() {
                            return a + 2;
                          }
                        }
                    """,
                """
                        class A {
                          private static int a = 1;
                        
                          private static int func1() {
                            return a;
                          }
                          
                          static int func2() {
                            return a + 1;
                          }
                          
                          private static int func3() {
                            return a + 2;
                          }
                        }
                    """
            )
        );
    }

    @Test
    void classMethodAccessed() {
        rewriteRun(
            java("""
                        class A {
                          private static int a = 1;
                        
                          private static int func1() {
                            return a;
                          }
                        
                          private int func2(int b) {
                            return func1() + b;
                          }
                        }
                    """,
                """
                        class A {
                          private static int a = 1;
                                            
                          private static int func1() {
                            return a;
                          }
                        
                          private static int func2(int b) {
                            return func1() + b;
                          }
                        }
                    """
            )
        );
    }

    // same name local variable with instance variable, can be static.
    @Test
    void sameNameLocalVariable() {
        rewriteRun(
            java("""
                        class A {
                          int a = 1;
                        
                          private int func(int b) {
                            int c = 2;
                            for (int i = 0; i < b ;i++) {
                              int a = 2;
                              c += a;
                            }
                            return c + 1;
                          }
                        }
                    """,
                """
                    class A {
                      int a = 1;
                                        
                      private static int func(int b) {
                        int c = 2;
                        for (int i = 0; i < b ;i++) {
                          int a = 2;
                          c += a;
                        }
                        return c + 1;
                      }
                    }
                    """
            )
        );
    }

    // same name local variable with instance variable, but instance variable is accessed out of scope.
    @Test
    void sameNameLocalVariableInScope() {
        rewriteRun(
            java("""
                        class A {
                          int a = 1;
                        
                          private int func(int b) {
                            int c = 2;
                            for (int i = 0; i < b ;i++) {
                              int a = 2;
                              c += a;
                            }
                            return c + a;
                          }
                        }
                    """
            )
        );
    }

    @Test
    void sameNameInstanceVariable() {
        rewriteRun(
            java("""
                        class A {
                          int a = 1;
                        
                          private int func(int b) {
                            int a = 2;
                            return a + b;
                          }
                        }
                    """,
                """
                        class A {
                          int a = 1;
                        
                          private static int func(int b) {
                            int a = 2;
                            return a + b;
                          }
                        }
                    """
            )
        );
    }

    @Test
    void sameNameClassVariableInScope() {
        rewriteRun(
            java("""
                        class A {
                          static int a = 1;
                        
                          private int func(int b) {
                            int c = 2;
                            for (int i = 0; i < b ;i++) {
                              int a = 2;
                              c += a;
                            }
                            return c + a;
                          }
                        }
                    """,
                """
                        class A {
                          static int a = 1;
                        
                          private static int func(int b) {
                            int c = 2;
                            for (int i = 0; i < b ;i++) {
                              int a = 2;
                              c += a;
                            }
                            return c + a;
                          }
                        }
                    """
            )
        );
    }

    @Test
    void InnerClassIgnored() {
        rewriteRun(
            java("""
                        class OuterClass {
                          int a;

                          class InnerClass {
                            private int func1(int x) {
                              return x + 1;
                            }
                          }
                        }
                    """
            )
        );
    }

    @Test
    void StaticNestedClassIgnored() {
        rewriteRun(
            java("""
                        class OuterClass {
                          int a = 1;

                          private static class InnerClass {
                            int b = 0;
                            int func2(int x) {
                              return x + 1;
                            }
                          }
                        }
                    """
            )
        );
    }

    @Test
    void methodNameIsSameWithInstanceVariable() {
        rewriteRun(
            java("""
                        class A {
                          int count = 1;
                        
                          private int count(int x) {
                            return count + x;
                          }
                        }
                    """
            )
        );
    }

    @Test
    void instanceMethodCalledInLambda() {
        rewriteRun(
            java("""
                        class A {
                          int a = 1;
                        
                          private int fun1(int x) {
                            return a + x;
                          }
                        
                          private int fun2(List<Integer> nums) {
                            return nums.stream().map(n -> {
                              return fun1(n);
                            }).findFirst().get();
                          }
                        }
                    """
            )
        );
    }

    @Test
    void recursive() {
        rewriteRun(
            java("""
                        class A {
                          private int func(int x) {
                            return x > 0 ? x : func(x) + 1;
                          }
                        }
                    """,
                """
                        class A {
                          private static int func(int x) {
                            return x > 0 ? x : func(x) + 1;
                          }
                        }
                    """
            )
        );
    }

    @Test
    void nestedInstanceMethods() {
        rewriteRun(
            java("""
                        class A {
                          private int a;
                        
                          private int func1(int x) {
                            return x + 1;
                          }
                        
                          private int func2(int x) {
                            return func1(x) + 1;
                          }
                        
                          private int func3(int x) {
                            return func2(x) + 1;
                          }
                           
                          private int func4(int x) {
                            return func3(x) + a;
                          }
                        }
                    """,
                """
                        class A {
                          private int a;
                        
                          private static int func1(int x) {
                            return x + 1;
                          }
                        
                          private static int func2(int x) {
                            return func1(x) + 1;
                          }
                        
                          private static int func3(int x) {
                            return func2(x) + 1;
                          }
                          
                          private int func4(int x) {
                            return func3(x) + a;
                          }
                        }
                    """
            )
        );
    }

    @Test
    void nestedCyclicInstanceMethods() {
        rewriteRun(
            java("""
                        class CollatzConjecture {
                          private void odd(int n) {
                            if (n != 1) {
                              even(3 * n + 1);
                            }
                          }
                        
                          private void even(int n) {
                            n = n /2;
                            if (n % 2 == 0) {
                              even(n);
                            } else {
                              odd(n);
                            }
                          }
                        }
                    """,
                """
                        class CollatzConjecture {
                          private static void odd(int n) {
                            if (n != 1) {
                              even(3 * n + 1);
                            }
                          }
                        
                          private static void even(int n) {
                            n = n /2;
                            if (n % 2 == 0) {
                              even(n);
                            } else {
                              odd(n);
                            }
                          }
                        }
                    """
            )
        );
    }

    @Test
    void subclassAccess() {
        rewriteRun(
            java("""
                        class A {
                            protected int a;
                            
                            protected int func1() {
                                return a;
                            }
                        }
                """
            ),
            java("""
                        class B extends A {
                            private int func2() {
                                return 1 + a;
                            }
                            
                            private int func3() {
                                return func1();
                            }
                        }
                """
            )
        );
    }

    @Test
    void overloadMethods() {
        rewriteRun(
            java("""
                        class A {
                            protected int a;
                        
                            private int func1(int x) {
                              return x + 1;
                            }
                        
                            private int func1() {
                                return a;
                            }
                        }
                """,
                    """
                        class A {
                            protected int a;
                        
                            private static int func1(int x) {
                              return x + 1;
                            }
                        
                            private int func1() {
                                return a;
                            }
                        }
                    """
                )
        );
    }
}
