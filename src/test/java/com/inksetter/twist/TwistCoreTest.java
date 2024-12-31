package com.inksetter.twist;

import com.inksetter.twist.exec.ScriptContext;
import com.inksetter.twist.expression.function.TwistFunction;
import com.inksetter.twist.parser.ScriptSyntaxException;
import com.inksetter.twist.parser.TwistParser;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TwistCoreTest {

    private final List<String> functionCalls = new ArrayList<>();
    private final List<List<Object>> functionArgs = new ArrayList<>();
    private final TwistEngine engine = new TwistEngine();

    private final Map<String, TwistFunction> functions = Map.of(
            "print", (args, context) -> {
                functionCalls.add("print");
                functionArgs.add(args);
                return 3.2;
            },
            "enumerate", (args, context) -> {
                return context.getAll();
            },
            "lookup", (args, context) -> {
                return context.getVariable(args.get(0).toString());
            });


    @Test
    public void testEmptyScript() throws TwistException {
        ScriptContext context = new SimpleScriptContext(Map.of(), functions);
        String script = "";
        Script parsed = engine.parseScript(script);
        assertNull(engine.parseScript(script).execute(context));

        script = "/* comment only */";
        assertNull(engine.parseScript(script).execute(context));

        script = "// single-line comment";
        assertNull(engine.parseScript(script).execute(context));

        script = """
                // single-line comment";
                // comment line two
                """;

        assertNull(engine.parseScript(script).execute(context));
    }

    @Test
    public void testMultipleStatements() throws TwistException {
        String script = """
                a = 100;
                b = a + 4;
                print('WOW ' + b);
                """;

        Script parsed = engine.parseScript(script);
        ScriptContext context = new SimpleScriptContext(Map.of(), functions);
        parsed.execute(context);
        Assert.assertEquals(100, context.getVariable("a"));
        Assert.assertEquals(104, context.getVariable("b"));
        Assert.assertEquals(1, functionCalls.size());
        Assert.assertEquals(1, functionArgs.size());
        Assert.assertEquals("print", functionCalls.get(0));
        Assert.assertEquals("WOW 104", functionArgs.get(0).get(0));
    }

    @Test
    public void testMultipleStatementsNoSemicolons() throws TwistException {
        String script = """
                a = 100
                b = a + 4
                print('WOW ' + b)
                """;

        Script parsed = engine.parseScript(script);
        ScriptContext context = new SimpleScriptContext(Map.of(), functions);
        parsed.execute(context);
        Assert.assertEquals(100, context.getVariable("a"));
        Assert.assertEquals(104, context.getVariable("b"));
        Assert.assertEquals(1, functionCalls.size());
        Assert.assertEquals(1, functionArgs.size());
        Assert.assertEquals("print", functionCalls.get(0));
        Assert.assertEquals("WOW 104", functionArgs.get(0).get(0));
    }

    @Test
    public void testMultipleStatementsNoSemicolonsPlusComments() throws TwistException {
        String script = """
                // set a to 100
                a = 100
                
                // Add 4, then store it in b
                b = a + 4
                
                /*
                 * Call the print() function
                 */
                print('WOW ' + b)
                """;

        Script parsed = engine.parseScript(script);
        ScriptContext context = new SimpleScriptContext(Map.of(), functions);
        parsed.execute(context);
        Assert.assertEquals(100, context.getVariable("a"));
        Assert.assertEquals(104, context.getVariable("b"));
        Assert.assertEquals(1, functionCalls.size());
        Assert.assertEquals(1, functionArgs.size());
        Assert.assertEquals("print", functionCalls.get(0));
        Assert.assertEquals("WOW 104", functionArgs.get(0).get(0));
    }

    @Test
    public void testSingleStatementNoSemicolon() throws TwistException {
        String script = "print('WOW ' + (100 + 4))";

        Script parsed = engine.parseScript(script);
        ScriptContext context = new SimpleScriptContext(Map.of(), functions);
        parsed.execute(context);
        Assert.assertEquals(1, functionCalls.size());
        Assert.assertEquals(1, functionArgs.size());
        Assert.assertEquals("print", functionCalls.get(0));
        Assert.assertEquals("WOW 104", functionArgs.get(0).get(0));
    }

    @Test
    public void testMultipleStatementsNoSemicolonsNoNewlines() throws TwistException {
        try {
            String script = "a = 100 " +
                            "b = a + 4 " +
                            "print('WOW ' + b)";

            engine.parseScript(script);
            fail("expected parser error");
        } catch (ScriptSyntaxException e) {
            // Normal
        }
    }

    @Test
    public void testMultipleInvocationsOnTheSameContext() throws TwistException {
        ScriptContext context = new SimpleScriptContext();
        Script parsed = engine.parseScript("a = 0");
        parsed.execute(context);
        Script increment = engine.parseScript("a = a + 1");
        for (int i = 0; i < 10000; i++) {
            increment.execute(context);
        }
        Assert.assertEquals(10000, context.getVariable("a"));
    }

    @Test
    public void testDateArithmetic() throws TwistException{
        ScriptContext context = new SimpleScriptContext();
        engine.parseScript("a = now(); b = a - 4.4; c = b - a; d = b + 8").execute(context);
        assertTrue(context.getVariable("a") instanceof Date);
        assertTrue(context.getVariable("b") instanceof Date);
        assertTrue(context.getVariable("c") instanceof Double);
        assertTrue(context.getVariable("d") instanceof Date);
        Date a = (Date) context.getVariable("a");
        Date b = (Date) context.getVariable("b");
        Double c = (Double) context.getVariable("c");
        Date d = (Date) context.getVariable("d");

        assertTrue(a.compareTo(b) > 0);
        assertTrue(a.compareTo(d) < 0);
        assertTrue(b.compareTo(d) < 0);
        assertEquals(4.4, c, 0.01);
    }

    public static class TestClass {
        public String getThing1() { return "aaaa"; }
        public String getThing2() { return "xxxx"; }
    }

    @Test
    public void testProperties() throws TwistException{
        Map<String, Object> testMap = new HashMap<>();

        testMap.put("a", "a-value");
        testMap.put("b", 3.14);

        TestClass testObj = new TestClass();

        ScriptContext context = new SimpleScriptContext(Map.of("foo", testMap, "bar", testObj), functions);

        engine.parseScript("a = bar.thing1; b = bar.thing2").execute(context);
        engine.parseScript("c = foo.a; d = foo.b").execute(context);

        Assert.assertEquals("aaaa", context.getVariable("a"));
        Assert.assertEquals("xxxx", context.getVariable("b"));
        Assert.assertEquals("a-value", context.getVariable("c"));
        Assert.assertEquals(3.14, context.getVariable("d"));
    }

    @Test
    public void testJavaMethods() throws TwistException {
        Map<String, Object> testMap = new HashMap<>();
        testMap.put("a", "abcdefg");

        ScriptContext context = new SimpleScriptContext(Map.of("foo", testMap), functions);

        engine.parseScript("aaa = foo.a; bbb = foo.a.substring(2,5); ccc = aaa.substring(3)").execute(context);

        Assert.assertEquals("abcdefg", context.getVariable("aaa"));
        Assert.assertEquals("cde", context.getVariable("bbb"));
        Assert.assertEquals("defg", context.getVariable("ccc"));
    }

    @Test
    public void testJavaMethodsWithInvalidArgs() throws TwistException {
        ScriptContext context = new SimpleScriptContext(Map.of("foo", "abcd"), functions);

        try {
            engine.parseScript("aaa = foo.substring(2,'900');").execute(context);
            fail("Expected exception");
        }
        catch (TwistException e) {
            // Normal
        }
    }

    @Test
    public void testJson() throws TwistException {
        ScriptContext context = new SimpleScriptContext(Map.of("foo", "{\"a\": 900}"), functions);

        engine.parseScript("aaa = eval(foo); bbb = aaa.a;").execute(context);
        Assert.assertEquals(900, context.getVariable("bbb"));
    }

    @Test
    public void testRawJson() throws TwistException {
        ScriptContext context = new SimpleScriptContext();

        engine.parseScript("aaa = {'a':900}; bbb = aaa.a;").execute(context);
        Assert.assertEquals(900, context.getVariable("bbb"));
        Object aaa = context.getVariable("aaa");
        Assert.assertTrue(aaa instanceof Map);
        Assert.assertEquals(900, ((Map<String, Object>)aaa).get("a"));
        Assert.assertEquals(900, context.getVariable("bbb"));
        engine.parseScript("ccc = {}").execute(context);
        Object ccc = context.getVariable("ccc");
        Assert.assertTrue(ccc instanceof Map);
        Assert.assertEquals(0, ((Map<?, ?>) ccc).size());
    }

    @Test
    public void testComplexJson() throws TwistException {
        ScriptContext context = new SimpleScriptContext();

        String script = """
                aaa = {
                   "version":1.0,
                   "data":[
                       {
                           "bookId": "0001",
                           "title": "my book",
                           "isbn": "fdsja910321"
                       },
                       {
                           "bookId": "0002",
                           "title": "my book too",
                           "isbn": "fdsja910311"
                       },
                       {
                           "bookId": "0009",
                           "title": "where the wild thing's are",
                           "isbn": "abchzzz0123"
                       }
                   ],
                   "transactionId": 902,
                   "timestamp": "2024-05-06T07:03:09"
                };
                b = aaa.data[2].isbn + "/" + aaa.version;
                """;

        engine.parseScript(script).execute(context);
        Assert.assertEquals("abchzzz0123/1", context.getVariable("b"));
    }

    @Test
    public void testRawJsonArray() throws TwistException {
        ScriptContext context = new SimpleScriptContext();

        engine.parseScript("zurg = 'hello'; aaa = [1,2,3,4,5]; bbb = ['a','b','grumph', zurg]").execute(context);
        Object aaa = context.getVariable("aaa");
        Assert.assertTrue(aaa instanceof List);
        Object bbb = context.getVariable("bbb");
        Assert.assertTrue(bbb instanceof List);
        Assert.assertEquals(2, ((List<Object>)aaa).get(1));
        Assert.assertEquals("hello", ((List<Object>)bbb).get(3));
        engine.parseScript("empty = []").execute(context);
        Object empty = context.getVariable("empty");
        Assert.assertTrue(empty instanceof List);
        Assert.assertEquals(0, ((List<?>) empty).size());

    }

    @Test
    public void testUserDefFunctions() throws TwistException {
        ScriptContext context = new SimpleScriptContext();

        context.setVariable("aaa", "hello");
        context.setVariable("bbb", "jello");
        context.setVariable("ccc", 76);
        Object result = engine.parseScript("def fff(a, b, c) { a + ' ' + b + ' ' + c }; fff(aaa, bbb,ccc)").execute(context);
        Assert.assertEquals("hello jello 76", result);
    }

    @Test
    public void testNestedUserDefFunctions() throws TwistException {
        ScriptContext context = new SimpleScriptContext();

        context.setVariable("aaa", "hello");
        context.setVariable("bbb", "jello");
        context.setVariable("ccc", 76);
        Object result = engine.parseScript("""
            def fff(a, b, c) {
                a + ' ' + b + ' ' + c
            }
            
            def ggg(aaa) {
                return fff(aaa, aaa, aaa)
            }
            
            fff(aaa, bbb,ccc) + '/' + ggg(bbb)
            """).execute(context);
        Assert.assertEquals("hello jello 76/jello jello jello", result);
    }

    @Test
    public void testUserDefFunctionCallingCoreFunction() throws TwistException {
        ScriptContext context = new SimpleScriptContext(Map.of(), functions);

        context.setVariable("aaa", "hello");
        context.setVariable("bbb", "jello");
        context.setVariable("ccc", 78);
        Object result = engine.parseScript("""
            def fff(a, b, c) {
                print(a + ' ' + b + ' ' + c)
            }
            
            fff(aaa, bbb,ccc)
            """).execute(context);
        Assert.assertEquals(1, functionCalls.size());
        Assert.assertEquals(1, functionArgs.size());
        Assert.assertEquals("print", functionCalls.get(0));
        Assert.assertEquals("hello jello 78", functionArgs.get(0).get(0));

    }

    public static class ExprTestObject {
        private String x = "banana";
        private int y = 23;

        public String getX() {
            return x;
        }

        public void setX(String x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }
    }

    @Test
    public void testExpression() throws TwistException {
        ScriptContext context = new SimpleScriptContext(Map.of("foo", new ExprTestObject()), functions);
        Assert.assertTrue(ValueUtils.asBoolean(new TwistParser("foo.x == 'banana'").parseExpression().evaluate(context)));
        Assert.assertFalse(ValueUtils.asBoolean(new TwistParser("foo.x != 'banana'").parseExpression().evaluate(context)));
    }


    @Test
    public void testRegex() throws TwistException {
        ScriptContext context = new SimpleScriptContext(Map.of("foo", new ExprTestObject()), functions);
        Assert.assertTrue(ValueUtils.asBoolean(new TwistParser("foo.x ==~ 'b.*'").parseExpression().evaluate(context)));
        Assert.assertFalse(ValueUtils.asBoolean(new TwistParser("foo.x ==~ 'ana'").parseExpression().evaluate(context)));
        Assert.assertTrue(ValueUtils.asBoolean(new TwistParser("foo.x =~ 'ana'").parseExpression().evaluate(context)));
        Assert.assertTrue(ValueUtils.asBoolean(new TwistParser("foo.x !~ 'apple'").parseExpression().evaluate(context)));

        // Regex prime check
        String script = "!('x'.repeat(bar) ==~ 'x?$|^(xx+?)\\1+')";
        Expression expr = new TwistParser(script).parseExpression();

        context.setVariable("bar", 631); // prime
        Assert.assertTrue(ValueUtils.asBoolean(expr.evaluate(context)));
        context.setVariable("bar", 637); // not prime
        Assert.assertFalse(ValueUtils.asBoolean(expr.evaluate(context)));
    }


    @Test
    public void testNumericExpression() throws TwistException {
        ScriptContext context = new SimpleScriptContext(Map.of("foo", new ExprTestObject()), functions);
        Assert.assertEquals(3, ValueUtils.asInt(new TwistParser("foo.y / 7 ").parseExpression().evaluate(context)));
        Assert.assertFalse(ValueUtils.asBoolean(new TwistParser("foo.y < 19").parseExpression().evaluate(context)));
    }


    @Test
    public void testInternalMethods() throws TwistException {
        ScriptContext context = new SimpleScriptContext(Map.of("foo", new ExprTestObject(), "bar", "spacey string   "), functions);
        Assert.assertEquals("nana", ValueUtils.asString(new TwistParser("foo.x.substring(2)").parseExpression().evaluate(context)));
        Assert.assertEquals("spacey string", ValueUtils.asString(new TwistParser("bar.strip()").parseExpression().evaluate(context)));
    }

    @Test
    public void testMemberAssignment() throws TwistException {
        ExprTestObject foo = new ExprTestObject();
        Map<String, Object> barMap = new LinkedHashMap<>();
        barMap.put("a", "hello");
        ScriptContext context = new SimpleScriptContext(Map.of("foo", foo, "bar", barMap), functions);
        TwistEngine t = new TwistEngine();
        Assert.assertEquals("banana", t.parseScript("foo.x").execute(context));
        Assert.assertEquals("hello", t.parseScript("bar.a").execute(context));
        t.parseScript("foo.x = 'apple'; bar.a = 'zoinks';").execute(context);

        Assert.assertEquals("apple", foo.getX());
        Assert.assertEquals("zoinks", barMap.get("a"));
    }

    @Test
    public void testElementAssignment() throws TwistException {
        String[] foo = new String[] {"one","two","three"};
        List<String> bar = new ArrayList<>(List.of("first", "second", "third"));

        ScriptContext context = new SimpleScriptContext(Map.of("foo", foo, "bar", bar), functions);
        TwistEngine t = new TwistEngine();
        Assert.assertEquals("two", t.parseScript("foo[1]").execute(context));
        Assert.assertEquals("first", t.parseScript("bar[0]").execute(context));
        t.parseScript("foo[1] = 'apple'; bar[0] = 'zoinks';").execute(context);

        Assert.assertEquals("apple", foo[1]);
        Assert.assertEquals("zoinks", bar.get(0));
    }

    @Test
    public void testEnumerate() throws TwistException {
        String[] foo = new String[] {"one","two","three"};
        List<String> bar = new ArrayList<>(List.of("first", "second", "third"));

        ScriptContext context = new SimpleScriptContext(Map.of("foo", foo, "bar", bar), functions);
        TwistEngine t = new TwistEngine();
        Object result = t.parseScript("return enumerate()").execute(context);

        assertTrue(result instanceof Map);
        Map<String, String> resultMap = (Map<String, String>) result;
        assertTrue(resultMap.containsKey("foo"));
        assertTrue(resultMap.containsKey("bar"));
    }

    @Test
    public void testEnumerateInsideFunction() throws TwistException {
        String[] foo = new String[] {"one","two","three"};
        List<String> bar = new ArrayList<>(List.of("first", "second", "third"));

        ScriptContext context = new SimpleScriptContext(Map.of("foo", foo, "bar", bar), functions);
        TwistEngine t = new TwistEngine();
        String script = """
                def myFunc(xxx) {
                    return enumerate()
                }
                return myFunc("test")
                """;
        Object result = t.parseScript(script).execute(context);

        assertTrue(result instanceof Map);
        Map<String, String> resultMap = (Map<String, String>) result;
        assertFalse(resultMap.containsKey("foo"));
        assertFalse(resultMap.containsKey("bar"));
        assertEquals("test", resultMap.get("xxx"));
    }

    @Test
    public void testLookupInsideFunction() throws TwistException {
        String[] foo = new String[] {"one","two","three"};
        List<String> bar = new ArrayList<>(List.of("first", "second", "third"));

        TwistEngine t = new TwistEngine();
        String script = """
                def myFunc(xxx) {
                    baz = "yes";
                    return lookup(xxx)
                }
                baz = "i am baz";
                return myFunc(blah)
                """;
        Script s = t.parseScript(script);

        ScriptContext context = new SimpleScriptContext(Map.of("foo", "i am foo", "bar", "i am bar"), functions);

        context.setVariable("blah", "foo");
        Object result = s.execute(context);
        assertEquals("i am foo", result);

        context.setVariable("blah", "fooz");
        result = s.execute(context);
        assertNull(result);

        context.setVariable("blah", "baz");
        result = s.execute(context);
        assertEquals("yes", result);

        context.setVariable("blah", "xxx");
        result = s.execute(context);
        assertEquals("xxx", result);
    }

    @Test
    public void testLookupAcrossFunctions() throws TwistException {
        String[] foo = new String[] {"one","two","three"};
        List<String> bar = new ArrayList<>(List.of("first", "second", "third"));

        TwistEngine t = new TwistEngine();
        String script = """
                def myFunc(yyy) {
                    baz2 = "yes too";
                    return lookup(yyy)
                }
                
                def myFun2(xxx) {
                    baz = "yes";
                    return myFunc(xxx)
                }
                baz = "i am baz";
                return myFun2(blah)
                """;
        Script s = t.parseScript(script);

        ScriptContext context = new SimpleScriptContext(Map.of("foo", "i am foo", "bar", "i am bar"), functions);

        context.setVariable("blah", "foo");
        Object result = s.execute(context);
        assertEquals("i am foo", result);

        context.setVariable("blah", "baz2");
        result = s.execute(context);
        assertEquals("yes too", result);

        context.setVariable("blah", "baz");
        result = s.execute(context);
        assertEquals("i am baz", result);

        context.setVariable("blah", "xxx");
        result = s.execute(context);
        assertNull(result);
    }




    @Test
    public void testChainedAssignment() throws TwistException {

        ScriptContext context = new SimpleScriptContext();
        TwistEngine t = new TwistEngine();
        t.parseScript("a = b = c = 'hello'; d = e = 23;").execute(context);

        Assert.assertEquals("hello", context.getVariable("a"));
        Assert.assertEquals("hello", context.getVariable("b"));
        Assert.assertEquals("hello", context.getVariable("c"));
        Assert.assertEquals(23, context.getVariable("d"));
        Assert.assertEquals(23, context.getVariable("e"));
    }

    @Test
    public void testSelfAssignmentOperators() throws TwistException {
        ScriptContext context = new SimpleScriptContext();
        TwistEngine t = new TwistEngine();
        t.parseScript("a = 100").execute(context);
        t.parseScript("a += 17").execute(context);
        Assert.assertEquals(117, context.getVariable("a"));
        t.parseScript("a -= 'hello'.length()").execute(context);
        Assert.assertEquals(112, context.getVariable("a"));
        t.parseScript("a /= (([3,4,5][0])-1)").execute(context);
        Assert.assertEquals(56, context.getVariable("a"));
        t.parseScript("a *= -3").execute(context);
        Assert.assertEquals(-168, context.getVariable("a"));
        t.parseScript("a = 'blah';a += 'z'; a += 'foo'").execute(context);
        Assert.assertEquals("blahzfoo", context.getVariable("a"));
    }

    public static class SideEffectTester {
        private int a = 0;
        private int getCalls = 0;
        private int setCalls = 0;

        public int getA() {
            getCalls++;
            return a;
        }

        public void setA(int a) {
            setCalls++;
            this.a = a;
        }
    }
    @Test
    public void testSelfAssignmentSideEffects() throws TwistException {
        ScriptContext context = new SimpleScriptContext();
        TwistEngine t = new TwistEngine();

        SideEffectTester testData = new SideEffectTester();
        context.setVariable("foo", testData);
        t.parseScript("foo.a += 10").execute(context);
        t.parseScript("foo.a -= 3").execute(context);
        Assert.assertEquals(7, testData.a);
        Assert.assertEquals(2, testData.setCalls);
        Assert.assertEquals(2, testData.getCalls);
    }

    @Test
    public void testForLoop() throws TwistException {
        ScriptContext context = new SimpleScriptContext();
        TwistEngine t = new TwistEngine();
        String script = "b = 30; for (a = 0; a < 10; a++) { b += 2 }";
        t.parseScript(script).execute(context);
        assertEquals(50, context.getVariable("b"));
    }
    @Test
    public void testForList() throws TwistException {
        ScriptContext context = new SimpleScriptContext();
        TwistEngine t = new TwistEngine();
        String script = "b = 30; for (a : [0,1,2,3,4]) { b += 2 }";
        t.parseScript(script).execute(context);
        assertEquals(40, context.getVariable("b"));
    }

    @Test
    public void testThrownException() throws TwistException {
        ScriptContext context = new SimpleScriptContext();
        TwistEngine t = new TwistEngine();
        String script = "a = 'abc'; b = int(a)";
        try {
            Object result = t.parseScript(script).execute(context);
            fail("expected exception, got " + result);
        }
        catch (NumberFormatException e) {
            // Normal
        }
    }

    @Test
    public void testTryCatch() throws TwistException {
        ScriptContext context = new SimpleScriptContext();
        TwistEngine t = new TwistEngine();
        String script = """
                try {
                    x = int('abc');
                }
                catch (NumberFormatException e) {
                   y = "BOOM";
                }
                """;

        Object result = t.parseScript(script).execute(context);
        assertEquals("BOOM", result);
    }
    @Test
    public void testTryCatchWithSuperclass() throws TwistException {
        ScriptContext context = new SimpleScriptContext();
        TwistEngine t = new TwistEngine();
        String script = """
                try {
                    x = int('abc');
                }
                catch (Exception e) {
                   y = "BOOMMM";
                }
                """;

        Object result = t.parseScript(script).execute(context);
        assertEquals("BOOMMM", result);
    }

    @Test
    public void testReturn() throws TwistException {
        ScriptContext context = new SimpleScriptContext();
        TwistEngine t = new TwistEngine();
        String script = """
                x = 1000;
                y = "no";
                if (x > 200) {
                   y = "yes";
                   return "BAM!";
                }
                x = 200;
                """;

        Object result = t.parseScript(script).execute(context);
        assertEquals("BAM!", result);
        assertEquals(1000, context.getVariable("x"));
        assertEquals("yes", context.getVariable("y"));
    }

    @Test
    public void testJsonFunction() throws TwistException {
        MapContext vars = new MapContext();
        TwistEngine t = new TwistEngine();
        Expression e = t.parseExpression("json(x)");
        Object result;
        vars.setVariable("x", "str");
        result = e.evaluate(vars);
        assertEquals("\"str\"", result);
        vars.setVariable("x", 1000);
        result = e.evaluate(vars);
        assertEquals("1000", result);
        Map<String, Object> obj = new LinkedHashMap<String, Object>();
        obj.put("aaa", "value");
        obj.put("bbb", -2);
        obj.put("ccc", List.of("1", "2", "3"));
        vars.setVariable("x", obj);
        result = e.evaluate(vars);
        assertEquals("{\"aaa\":\"value\",\"bbb\":-2,\"ccc\":[\"1\",\"2\",\"3\"]}", result);
        obj.put("ddd", null);
        result = e.evaluate(vars);
        assertEquals("{\"aaa\":\"value\",\"bbb\":-2,\"ccc\":[\"1\",\"2\",\"3\"],\"ddd\":null}", result);
    }

    @Test
    public void testJsonFunctionPretty() throws TwistException {
        MapContext vars = new MapContext();
        TwistEngine t = new TwistEngine();
        Expression e = t.parseExpression("json(x, true)");
        Object result;
        vars.setVariable("x", "str");
        result = e.evaluate(vars);
        assertEquals("\"str\"", result);
        vars.setVariable("x", 1000);
        result = e.evaluate(vars);
        assertEquals("1000", result);
        Map<String, Object> obj = new LinkedHashMap<String, Object>();
        obj.put("aaa", "value");
        obj.put("bbb", -2);
        obj.put("ccc", List.of("1", "2", "3"));
        vars.setVariable("x", obj);
        result = e.evaluate(vars);
        assertEquals(
                """
                        {
                          "aaa":"value",
                          "bbb":-2,
                          "ccc":[
                            "1",
                            "2",
                            "3"
                          ]
                        }""", result);
        obj.put("ddd", null);
        result = e.evaluate(vars);
        assertEquals("""
                {
                  "aaa":"value",
                  "bbb":-2,
                  "ccc":[
                    "1",
                    "2",
                    "3"
                  ],
                  "ddd":null
                }""", result);
    }

    @Test
    public void testMethodInterceptor() throws TwistException {
        MethodInterceptor interceptor = (name, args) ->
                "calling " + name + "(" + Arrays.asList(args) + ")";
        ScriptContext context = new SimpleScriptContext(Map.of("xxx", interceptor), functions);
        TwistEngine t = new TwistEngine();
        Object result = t.parseScript("xxx.myMethod(100, 'ABC')").execute(context);
        assertEquals("calling myMethod([100, ABC])", result);
    }
}
