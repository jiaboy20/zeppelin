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

package org.apache.zeppelin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.rest.message.InterpreterInstallationRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterpreterServiceTest {
  private ZeppelinConfiguration zConf;
  private InterpreterSettingManager mockInterpreterSettingManager;

  private Path temporaryDir;
  private Path interpreterDir;
  private Path localRepoDir;

  InterpreterService interpreterService;

  @BeforeEach
  public void setUp() throws Exception {
    zConf = mock(ZeppelinConfiguration.class);
    mockInterpreterSettingManager = mock(InterpreterSettingManager.class);
    temporaryDir = Files.createTempDirectory("tmp");
    interpreterDir = Files.createTempDirectory(temporaryDir, "interpreter");
    localRepoDir = Files.createTempDirectory(temporaryDir, "local-repo");

    when(zConf.getInterpreterDir()).thenReturn(interpreterDir.toString());
    when(zConf.getInterpreterLocalRepoPath())
        .thenReturn(localRepoDir.toString());

    interpreterService =
        new InterpreterService(zConf, mockInterpreterSettingManager);
  }

  @AfterEach
  public void tearDown() throws Exception {
    if (null != temporaryDir) {
      FileUtils.deleteDirectory(temporaryDir.toFile());
    }
  }

  @Test
  void interpreterAlreadyExist() throws IOException {
    String alreadyExistName = "aen";
    Path specificInterpreterDir =
      Files.createDirectory(Paths.get(interpreterDir.toString(), alreadyExistName));
    assertThrows(Exception.class, () -> {
      interpreterService.installInterpreter(
        new InterpreterInstallationRequest(alreadyExistName, "artifact"), null);
    });
  }

  @Test
  void interpreterAlreadyExistWithDifferentName() throws IOException {
    String interpreterName = "in";
    Files.createDirectory(Paths.get(interpreterDir.toString(), interpreterName));

    String anotherButSameInterpreterName = "zeppelin-" + interpreterName;
    assertThrows(Exception.class, () -> {
      interpreterService.installInterpreter(
        new InterpreterInstallationRequest(anotherButSameInterpreterName, "artifact"), null);
    });
  }

  @Test
  void downloadInterpreter() throws IOException {
    final String interpreterName = "test-interpreter";
    String artifactName = "junit:junit:4.11";
    Path specificInterpreterPath =
        Files.createDirectory(Paths.get(interpreterDir.toString(), interpreterName));
    DependencyResolver dependencyResolver =
        new DependencyResolver(localRepoDir.toString(), ZeppelinConfiguration.load());

    doNothing().when(mockInterpreterSettingManager).refreshInterpreterTemplates();

    interpreterService.downloadInterpreter(
        new InterpreterInstallationRequest(interpreterName, artifactName),
        dependencyResolver,
        specificInterpreterPath,
        new SimpleServiceCallback<String>() {
          @Override
          public void onStart(String message, ServiceContext context) {
            assertEquals("Starting to download " + interpreterName + " interpreter", message);
          }

          @Override
          public void onSuccess(String message, ServiceContext context) {
            assertEquals(interpreterName + " downloaded", message);
          }

          @Override
          public void onFailure(Exception ex, ServiceContext context) {
          fail(ex);
          }
        });

    verify(mockInterpreterSettingManager, times(1)).refreshInterpreterTemplates();
  }
}
