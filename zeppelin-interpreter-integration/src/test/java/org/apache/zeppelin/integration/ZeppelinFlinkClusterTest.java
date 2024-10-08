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

import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.test.DownloadUtils;
import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;


public abstract class ZeppelinFlinkClusterTest extends AbstractTestRestApi {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeppelinFlinkClusterTest.class);
  private String flinkHome;

  public void download(String flinkVersion, String scalaVersion) {
    LOGGER.info("Testing FlinkVersion: " + flinkVersion);
    LOGGER.info("Testing ScalaVersion: " + scalaVersion);
    this.flinkHome = DownloadUtils.downloadFlink(flinkVersion, scalaVersion);
  }

  private static MiniZeppelinServer zepServer;

  @BeforeAll
  static void init() throws Exception {
    zepServer = new MiniZeppelinServer(ZeppelinFlinkClusterTest.class.getSimpleName());
    zepServer.addInterpreter("sh");
    zepServer.addInterpreter("flink");
    zepServer.addInterpreter("flink-cmd");
    zepServer.copyBinDir();
    zepServer.addLauncher("FlinkInterpreterLauncher");
    zepServer.getZeppelinConfiguration().setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HELIUM_REGISTRY.getVarName(),
        "helium");
    zepServer.start();
  }

  @AfterAll
  static void destroy() throws Exception {
    zepServer.destroy();
  }

  @BeforeEach
  void setup() {
    zConf = zepServer.getZeppelinConfiguration();
  }

  @Disabled("(zjffdu) Disable Temporary")
  @Test
  public void testResumeFromCheckpoint() throws Exception {

    String noteId = null;
    try {
      // create new note
      noteId = zepServer.getService(Notebook.class).createNote("note1", AuthenticationInfo.ANONYMOUS);

      // run p0 for %flink.conf
      String checkpointPath = Files.createTempDirectory("checkpoint").toAbsolutePath().toString();
      zepServer.getService(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p0 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          StringBuilder builder = new StringBuilder("%flink.conf\n");
          builder.append("FLINK_HOME " + flinkHome + "\n");
          builder.append("flink.execution.mode local\n");
          builder.append("state.checkpoints.dir file://" + checkpointPath + "\n");
          builder.append("execution.checkpointing.externalized-checkpoint-retention RETAIN_ON_CANCELLATION");
          p0.setText(builder.toString());
          note.run(p0.getId(), true);
          assertEquals(Job.Status.FINISHED, p0.getStatus());

          // run p1 for creating flink table via scala
          Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          p1.setText("%flink " + getInitStreamScript(2000));
          note.run(p1.getId(), true);
          assertEquals(Job.Status.FINISHED, p0.getStatus());

          // run p2 for flink streaming sql
          Paragraph p2 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          p2.setText("%flink.ssql(type=single, template=<h1>Total: {0}</h1>, resumeFromLatestCheckpoint=true)\n" +
                  "select count(1) from log;");
          note.run(p2.getId(), false);
          try {
            p2.waitUntilRunning();
            Thread.sleep(60 * 1000);
            p2.abort();
            // Sleep 5 seconds to ensure checkpoint info is written to note file
            Thread.sleep(5 * 1000);
            assertTrue(p2.getConfig().get("latest_checkpoint_path").toString().contains(checkpointPath), p2.getConfig().toString());
          } catch (InterruptedException e) {
            fail();
          }

          // run it again
          note.run(p0.getId(), true);
          note.run(p1.getId(), true);
          note.run(p2.getId(), false);
          try {
            p2.waitUntilFinished();
          } catch (InterruptedException e) {
            fail();
          }
          assertEquals(Job.Status.FINISHED, p2.getStatus(), p2.getReturn().toString());
          return null;
        });
    } catch (Exception e) {
      e.printStackTrace();
      throw e;
    } finally {
      if (null != noteId) {
        zepServer.getService(Notebook.class).removeNote(noteId, AuthenticationInfo.ANONYMOUS);
      }
    }
  }

  @Disabled
  @Test
  public void testResumeFromInvalidCheckpoint() throws Exception {

    String noteId = null;
    try {
      // create new note
      noteId = zepServer.getService(Notebook.class).createNote("note2", AuthenticationInfo.ANONYMOUS);

      // run p0 for %flink.conf
      String checkpointPath = Files.createTempDirectory("checkpoint").toAbsolutePath().toString();
      zepServer.getService(Notebook.class).processNote(noteId,
        note -> {
          Paragraph p0 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          StringBuilder builder = new StringBuilder("%flink.conf\n");
          builder.append("FLINK_HOME " + flinkHome + "\n");
          builder.append("flink.execution.mode local\n");
          builder.append("state.checkpoints.dir file://" + checkpointPath + "\n");
          builder.append("execution.checkpointing.externalized-checkpoint-retention RETAIN_ON_CANCELLATION");
          p0.setText(builder.toString());
          note.run(p0.getId(), true);
          assertEquals(Job.Status.FINISHED, p0.getStatus());

          // run p1 for creating flink table via scala
          Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          p1.setText("%flink " + getInitStreamScript(500));
          note.run(p1.getId(), true);
          assertEquals(Job.Status.FINISHED, p0.getStatus());

          // run p2 for flink streaming sql
          Paragraph p2 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          p2.setText("%flink.ssql(type=single, template=<h1>Total: {0}</h1>, resumeFromLatestCheckpoint=true)\n" +
                  "select count(1) from log;");
          p2.getConfig().put("latest_checkpoint_path", "file:///invalid_checkpoint");
          note.run(p2.getId(), false);
          try {
            p2.waitUntilFinished();
          } catch (InterruptedException e) {
            fail();
          }
          assertEquals(Job.Status.ERROR, p2.getStatus(), p2.getReturn().toString());
          assertTrue(p2.getReturn().toString().contains("Cannot find checkpoint"), p2.getReturn().toString());

          p2.setText("%flink.ssql(type=single, template=<h1>Total: {0}</h1>, resumeFromLatestCheckpoint=false)\n" +
                  "select count(1) from log;");
          note.run(p2.getId(), false);
          try {
            p2.waitUntilFinished();
          } catch (InterruptedException e) {
            fail();
          }
          assertEquals(Job.Status.FINISHED, p2.getStatus(), p2.getReturn().toString());
          return null;
        });

    } catch (Exception e) {
      e.printStackTrace();
      throw e;
    } finally {
      if (null != noteId) {
        zepServer.getService(Notebook.class).removeNote(noteId, AuthenticationInfo.ANONYMOUS);
      }
    }
  }

  public static String getInitStreamScript(int sleep_interval) throws IOException {
    return IOUtils.toString(FlinkIntegrationTest.class.getResource("/init_stream.scala"), StandardCharsets.UTF_8)
            .replace("{{sleep_interval}}", sleep_interval + "");
  }
}
