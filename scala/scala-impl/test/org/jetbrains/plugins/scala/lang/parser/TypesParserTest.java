package org.jetbrains.plugins.scala.lang.parser;

import junit.framework.Test;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

@RunWith(AllTests.class)
public class TypesParserTest extends AbstractParserTest {

    public TypesParserTest() {
        super("parser", "data", "types");
    }

    public static Test suite() {
        return new TypesParserTest();
    }
}
