/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.integration;

import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.interpreter.ExecutionContext;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;


public class JdbcIntegrationTest {

  private static InterpreterFactory interpreterFactory;
  private static InterpreterSettingManager interpreterSettingManager;
  private static MiniZeppelinServer zepServer;


  @BeforeAll
  public static void setUp() throws Exception {
    zepServer = new MiniZeppelinServer(JdbcIntegrationTest.class.getSimpleName());
    zepServer.addInterpreter("jdbc");
    zepServer.addInterpreter("python");
    zepServer.copyBinDir();
    zepServer.copyLogProperties();
    zepServer.start();
  }

  @BeforeEach
  void setup() {
    interpreterSettingManager = zepServer.getService(InterpreterSettingManager.class);
    interpreterFactory = new InterpreterFactory(interpreterSettingManager);
  }

  @AfterAll
  public static void tearDown() throws Exception {
    zepServer.destroy();
  }

  @Test
  void testMySql() throws InterpreterException, InterruptedException {
    InterpreterSetting interpreterSetting = interpreterSettingManager.getInterpreterSettingByName("jdbc");
    interpreterSetting.setProperty("default.driver", "com.mysql.jdbc.Driver");
    interpreterSetting.setProperty("default.url", "jdbc:mysql://localhost:3306/");
    interpreterSetting.setProperty("default.user", "root");
    interpreterSetting.setProperty("default.password", "root");

    Dependency dependency = new Dependency("mysql:mysql-connector-java:5.1.49");
    interpreterSetting.setDependencies(Arrays.asList(dependency));
    interpreterSettingManager.restart(interpreterSetting.getId());
    interpreterSetting.waitForReady(60 * 1000);
    Interpreter jdbcInterpreter = interpreterFactory.getInterpreter("jdbc", new ExecutionContext("user1", "note1", "test"));
    assertNotNull(jdbcInterpreter, "JdbcInterpreter is null");

    InterpreterContext context = new InterpreterContext.Builder()
            .setNoteId("note1")
            .setParagraphId("paragraph_1")
            .setAuthenticationInfo(AuthenticationInfo.ANONYMOUS)
            .build();
    InterpreterResult interpreterResult = jdbcInterpreter.interpret("show databases;", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code(), interpreterResult.toString());

    context.getLocalProperties().put("saveAs", "table_1");
    interpreterResult = jdbcInterpreter.interpret("SELECT 1 as c1, 2 as c2;", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code(), interpreterResult.toString());
    assertEquals(1, interpreterResult.message().size());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals("c1\tc2\n1\t2\n", interpreterResult.message().get(0).getData());

    // read table_1 from python interpreter
    InterpreterSetting pythonInterpreterSetting = interpreterSettingManager.getInterpreterSettingByName("python");
    pythonInterpreterSetting.setProperty("zeppelin.python.gatewayserver_address", "127.0.0.1");

    Interpreter pythonInterpreter = interpreterFactory.getInterpreter("python", new ExecutionContext("user1", "note1", "test"));
    assertNotNull(pythonInterpreter, "PythonInterpreter is null");

    context = new InterpreterContext.Builder()
            .setNoteId("note1")
            .setParagraphId("paragraph_1")
            .setAuthenticationInfo(AuthenticationInfo.ANONYMOUS)
            .build();
    interpreterResult = pythonInterpreter.interpret("df=z.getAsDataFrame('table_1')\nz.show(df)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code(), interpreterResult.toString());
    assertEquals(1, interpreterResult.message().size());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals("c1\tc2\n1\t2\n", interpreterResult.message().get(0).getData());
  }
}
