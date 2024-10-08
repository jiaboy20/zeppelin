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

package org.apache.zeppelin.notebook.repo;

import com.google.common.collect.ImmutableMap;

import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.GsonNoteParser;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.NoteParser;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VFSNotebookRepoTest {

  private ZeppelinConfiguration zConf;
  private NoteParser noteParser;
  private VFSNotebookRepo notebookRepo;

  @BeforeEach
  public void setUp() throws IOException {
    File notebookDir = Files.createTempDirectory(this.getClass().getSimpleName()).toFile();
    zConf = ZeppelinConfiguration.load();
    noteParser = new GsonNoteParser(zConf);
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), notebookDir.getAbsolutePath());
    notebookRepo = new VFSNotebookRepo();
    notebookRepo.init(zConf, noteParser);
  }

  @AfterEach
  public void tearDown() throws IOException {
    File rootDir = new File(notebookRepo.rootNotebookFolder);
    FileUtils.deleteDirectory(rootDir);
  }

  @Test
  void testBasics() throws IOException {
    assertEquals(0, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());

    // create note1
    Note note1 = new Note();
    note1.setPath("/my_project/my_note1");
    note1.setNoteParser(noteParser);
    Paragraph p1 = note1.insertNewParagraph(0, AuthenticationInfo.ANONYMOUS);
    p1.setText("%md hello world");
    p1.setTitle("my title");
    notebookRepo.save(note1, AuthenticationInfo.ANONYMOUS);

    Map<String, NoteInfo> noteInfos = notebookRepo.list(AuthenticationInfo.ANONYMOUS);
    assertEquals(1, noteInfos.size());
    assertEquals(note1.getId(), noteInfos.get(note1.getId()).getId());
    assertEquals(note1.getName(), noteInfos.get(note1.getId()).getNoteName());

    // create note2
    Note note2 = new Note();
    note2.setPath("/my_note2");
    note2.setNoteParser(noteParser);
    Paragraph p2 = note2.insertNewParagraph(0, AuthenticationInfo.ANONYMOUS);
    p2.setText("%md hello world2");
    p2.setTitle("my title2");
    notebookRepo.save(note2, AuthenticationInfo.ANONYMOUS);

    noteInfos = notebookRepo.list(AuthenticationInfo.ANONYMOUS);
    assertEquals(2, noteInfos.size());

    // move note2
    String newPath = "/my_project2/my_note2";
    notebookRepo.move(note2.getId(), note2.getPath(), "/my_project2/my_note2", AuthenticationInfo.ANONYMOUS);

    Note note3 = notebookRepo.get(note2.getId(), newPath, AuthenticationInfo.ANONYMOUS);
    note3.setNoteParser(noteParser);
    assertEquals(note2, note3);

    // move folder
    notebookRepo.move("/my_project2", "/my_project3/my_project2", AuthenticationInfo.ANONYMOUS);
    noteInfos = notebookRepo.list(AuthenticationInfo.ANONYMOUS);
    assertEquals(2, noteInfos.size());

    Note note4 = notebookRepo.get(note3.getId(), "/my_project3/my_project2/my_note2", AuthenticationInfo.ANONYMOUS);
    note4.setNoteParser(noteParser);
    assertEquals(note3, note4);

    // remote note1
    notebookRepo.remove(note1.getId(), note1.getPath(), AuthenticationInfo.ANONYMOUS);
    assertEquals(1, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());
  }

  @Test
  void testNoteNameWithColon() throws IOException {
    assertEquals(0, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());

    // create note with colon in name
    Note note1 = new Note();
    note1.setPath("/my_project/my:note1");
    note1.setNoteParser(noteParser);
    Paragraph p1 = note1.insertNewParagraph(0, AuthenticationInfo.ANONYMOUS);
    p1.setText("%md hello world");
    p1.setTitle("my title");
    notebookRepo.save(note1, AuthenticationInfo.ANONYMOUS);
    Map<String, NoteInfo> noteInfos = notebookRepo.list(AuthenticationInfo.ANONYMOUS);
    assertEquals(1, noteInfos.size());
  }

  @Test
  void testUpdateSettings() throws IOException {
    List<NotebookRepoSettingsInfo> repoSettings = notebookRepo.getSettings(AuthenticationInfo.ANONYMOUS);
    assertEquals(1, repoSettings.size());
    NotebookRepoSettingsInfo settingInfo = repoSettings.get(0);
    assertEquals("Notebook Path", settingInfo.name);
    assertEquals(notebookRepo.rootNotebookFolder, settingInfo.selected);

    createNewNote("{}", "id2", "my_project/name2");
    assertEquals(1, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());

    String newNotebookDir = "/tmp/zeppelin/vfs_notebookrepo2";
    FileUtils.forceMkdir(new File(newNotebookDir));
    Map<String, String> newSettings = ImmutableMap.of("Notebook Path", newNotebookDir);
    notebookRepo.updateSettings(newSettings, AuthenticationInfo.ANONYMOUS);
    assertEquals(0, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());
  }

  @Test
  void testSkipInvalidFileName() throws IOException {
    assertEquals(0, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());

    createNewNote("{}", "hidden_note", "my_project/.hidden_note");

    Map<String, NoteInfo> noteInfos = notebookRepo.list(AuthenticationInfo.ANONYMOUS);
    assertEquals(0, noteInfos.size());
  }

  @Test
  void testSkipInvalidDirectoryName() throws IOException {
    createNewDirectory(".hidden_dir");

    createNewNote("{}", "hidden_note", "my_project/.hidden_dir/note");

    Map<String, NoteInfo> noteInfos = notebookRepo.list(AuthenticationInfo.ANONYMOUS);
    assertEquals(0, noteInfos.size());
  }

  private void createNewNote(String content, String noteId, String noteName) throws IOException {
    FileUtils.writeStringToFile(
        new File(notebookRepo.rootNotebookFolder + "/" + noteName + "_" + noteId + ".zpln"), content, StandardCharsets.UTF_8);
  }

  private void createNewDirectory(String dirName) {
    File dir = new File(notebookRepo.rootNotebookFolder + "/" + dirName);
    dir.mkdir();
  }
}
