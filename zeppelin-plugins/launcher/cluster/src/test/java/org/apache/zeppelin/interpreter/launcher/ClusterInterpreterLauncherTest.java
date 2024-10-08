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
package org.apache.zeppelin.interpreter.launcher;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterRunningProcess;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Properties;

class ClusterInterpreterLauncherTest extends ClusterMockTest {

  @BeforeAll
  static void startTest() throws IOException, InterruptedException {
    ClusterMockTest.startCluster();
  }

  @AfterAll
  static void stopTest() throws IOException, InterruptedException {
    ClusterMockTest.stopCluster();
  }

  @BeforeEach
  void setUp() {
    for (final ZeppelinConfiguration.ConfVars confVar : ZeppelinConfiguration.ConfVars.values()) {
      System.clearProperty(confVar.getVarName());
    }
  }

  // TODO(zjffdu) disable this test because this is not a correct unit test,
  // Actually the interpreter process here never start before ZEPPELIN-5300.
  // @Test
  void testConnectExistOnlineIntpProcess() throws IOException {
    mockIntpProcessMeta("intpGroupId", true);

    ClusterInterpreterLauncher launcher =
        new ClusterInterpreterLauncher(ClusterMockTest.zConf, null);
    Properties properties = new Properties();
    properties.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName(), "5000");
    InterpreterOption option = new InterpreterOption();
    option.setUserImpersonate(true);
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null,
        "user1", "intpGroupId", "groupId",
        "groupName", "name", 0, "host");

    InterpreterClient client = launcher.launch(context);

    assertTrue(client instanceof RemoteInterpreterRunningProcess);
    RemoteInterpreterRunningProcess interpreterProcess = (RemoteInterpreterRunningProcess) client;
    assertEquals("127.0.0.1", interpreterProcess.getHost());
    assertEquals("name", interpreterProcess.getInterpreterSettingName());
    assertEquals(5000, interpreterProcess.getConnectTimeout());
    interpreterProcess.close();
  }

  @Test
  void testConnectExistOfflineIntpProcess() throws IOException {
    mockIntpProcessMeta("intpGroupId2", false);

    ClusterInterpreterLauncher launcher =
        new ClusterInterpreterLauncher(ClusterMockTest.zConf, null);
    Properties properties = new Properties();
    properties.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName(), "5000");
    InterpreterOption option = new InterpreterOption();
    option.setUserImpersonate(true);
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null,
        "user1", "intpGroupId2", "groupId",
        "groupName", "name", 0, "host");
    InterpreterClient client = launcher.launch(context);

    assertTrue(client instanceof ClusterInterpreterProcess);
    ClusterInterpreterProcess interpreterProcess = (ClusterInterpreterProcess) client;
    assertEquals("name", interpreterProcess.getInterpreterSettingName());
    assertEquals(".//interpreter/groupName", interpreterProcess.getInterpreterDir());
    assertEquals(".//local-repo/groupId", interpreterProcess.getLocalRepoDir());
    assertEquals(5000, interpreterProcess.getConnectTimeout());
    assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
    assertTrue(interpreterProcess.getEnv().size() >= 1);
    assertEquals(true, interpreterProcess.isUserImpersonated());
    interpreterProcess.close();
  }

  @Test
  void testCreateIntpProcessDockerMode() throws IOException {
    zConf.setRunMode(ZeppelinConfiguration.RUN_MODE.DOCKER);

    ClusterInterpreterLauncher launcher = new ClusterInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName(), "1000");
    InterpreterOption option = new InterpreterOption();
    option.setUserImpersonate(true);
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null,
        "user1", "intpGroupId3", "groupId3",
        "groupName", "name", 0, "host");
    InterpreterClient client = launcher.launch(context);

    assertTrue(client instanceof DockerInterpreterProcess);
  }

  @Test
  void testCreateIntpProcessLocalMode() throws IOException {
    zConf.setRunMode(ZeppelinConfiguration.RUN_MODE.LOCAL);

    ClusterInterpreterLauncher launcher = new ClusterInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName(), "1000");
    InterpreterOption option = new InterpreterOption();
    option.setUserImpersonate(true);
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null,
        "user1", "intpGroupId4", "groupId4",
        "groupName", "name", 0, "host");
    InterpreterClient client = launcher.launch(context);

    assertTrue(client instanceof ClusterInterpreterProcess);
  }
}
