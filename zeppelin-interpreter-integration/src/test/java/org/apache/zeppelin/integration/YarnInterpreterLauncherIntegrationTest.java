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

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationsRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationsResponse;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.EnumSet;

public class YarnInterpreterLauncherIntegrationTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(YarnInterpreterLauncherIntegrationTest.class);

  private static MiniHadoopCluster hadoopCluster;
  private static MiniZeppelinServer zepServer;
  private static InterpreterFactory interpreterFactory;
  private static InterpreterSettingManager interpreterSettingManager;

  @BeforeAll
  static void init() throws Exception {
    Configuration conf = new Configuration();
    hadoopCluster = new MiniHadoopCluster(conf);
    hadoopCluster.start();

    zepServer = new MiniZeppelinServer(YarnInterpreterLauncherIntegrationTest.class.getSimpleName());
    zepServer.addInterpreter("sh");
    zepServer.addInterpreter("jdbc");
    zepServer.addInterpreter("python");
    zepServer.addLauncher("YarnInterpreterLauncher");
    zepServer.copyLogProperties();
    zepServer.copyBinDir();
    zepServer.getZeppelinConfiguration().setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HELIUM_REGISTRY.getVarName(),
        "helium");
    zepServer.getZeppelinConfiguration().setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_ALLOWED_ORIGINS.getVarName(), "*");
    zepServer.getZeppelinConfiguration().setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName(), "120000");

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
    if (hadoopCluster != null) {
      hadoopCluster.stop();
    }
  }

  @Test
  void testLaunchShellInYarn() throws YarnException, InterpreterException, InterruptedException {
    InterpreterSetting shellInterpreterSetting = interpreterSettingManager.getInterpreterSettingByName("sh");
    shellInterpreterSetting.setProperty("zeppelin.interpreter.launcher", "yarn");
    shellInterpreterSetting.setProperty("HADOOP_CONF_DIR", hadoopCluster.getConfigPath());

    Interpreter shellInterpreter = interpreterFactory.getInterpreter("sh", new ExecutionContext("user1", "note1", "sh"));

    InterpreterContext context = new InterpreterContext.Builder().setNoteId("note1").setParagraphId("paragraph_1").build();
    InterpreterResult interpreterResult = shellInterpreter.interpret("pwd", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertTrue(interpreterResult.message().get(0).getData().contains("/usercache/"), interpreterResult.toString());

    Thread.sleep(1000);
    // 1 yarn application launched
    GetApplicationsRequest request = GetApplicationsRequest.newInstance(EnumSet.of(YarnApplicationState.RUNNING));
    GetApplicationsResponse response = hadoopCluster.getYarnCluster().getResourceManager().getClientRMService().getApplications(request);
    assertEquals(1, response.getApplicationList().size());

    interpreterSettingManager.close();
  }

  @Test
  void testJdbcPython_YarnLauncher() throws InterpreterException, YarnException, InterruptedException {
    InterpreterSetting jdbcInterpreterSetting = interpreterSettingManager.getInterpreterSettingByName("jdbc");
    jdbcInterpreterSetting.setProperty("default.driver", "com.mysql.jdbc.Driver");
    jdbcInterpreterSetting.setProperty("default.url", "jdbc:mysql://localhost:3306/");
    jdbcInterpreterSetting.setProperty("default.user", "root");
    jdbcInterpreterSetting.setProperty("zeppelin.interpreter.launcher", "yarn");
    jdbcInterpreterSetting.setProperty("zeppelin.interpreter.yarn.resource.memory", "512");
    jdbcInterpreterSetting.setProperty("HADOOP_CONF_DIR", hadoopCluster.getConfigPath());

    Dependency dependency = new Dependency("mysql:mysql-connector-java:5.1.46");
    jdbcInterpreterSetting.setDependencies(Arrays.asList(dependency));
    interpreterSettingManager.restart(jdbcInterpreterSetting.getId());
    jdbcInterpreterSetting.waitForReady(60 * 1000);

    InterpreterSetting pythonInterpreterSetting = interpreterSettingManager.getInterpreterSettingByName("python");
    pythonInterpreterSetting.setProperty("zeppelin.interpreter.launcher", "yarn");
    pythonInterpreterSetting.setProperty("zeppelin.interpreter.yarn.resource.memory", "512");
    pythonInterpreterSetting.setProperty("HADOOP_CONF_DIR", hadoopCluster.getConfigPath());

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

    // 2 yarn application launched
    GetApplicationsRequest request = GetApplicationsRequest.newInstance(EnumSet.of(YarnApplicationState.RUNNING));
    GetApplicationsResponse response = hadoopCluster.getYarnCluster().getResourceManager().getClientRMService().getApplications(request);
    assertEquals(2, response.getApplicationList().size());

    interpreterSettingManager.close();

    // sleep for 5 seconds to make sure yarn apps are finished
    Thread.sleep(5* 1000);
    request = GetApplicationsRequest.newInstance(EnumSet.of(YarnApplicationState.RUNNING));
    response = hadoopCluster.getYarnCluster().getResourceManager().getClientRMService().getApplications(request);
    assertEquals(0, response.getApplicationList().size());
  }
}
