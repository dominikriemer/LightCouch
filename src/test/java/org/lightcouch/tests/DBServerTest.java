/*
 * Copyright (C) 2011 lightcouch.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lightcouch.tests;

import org.junit.Test;
import org.lightcouch.CouchDbInfo;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;

public class DBServerTest extends CouchDbTestBase {

  @Test
  public void dbInfo() {
    CouchDbInfo dbInfo = dbClient.context().info();
    assertNotNull(dbInfo);
    if (isCouchDB2()) {
      assertNotNull(dbInfo.getSizes());
      assertNotNull(dbInfo.getCluster());
    }
    if (isCouchDB3()) {
      assertNotNull(dbInfo.getProps());
    }
    System.out.println(dbInfo);
  }

  @Test
  public void serverVersion() {
    String version = dbClient.context().serverVersion();
    assertNotNull(version);
  }

  @Test
  public void compactDb() {
    dbClient.context().compact();
  }

  @Test
  public void allDBs() {
    List<String> allDbs = dbClient.context().getAllDbs();
    assertThat(allDbs.size(), is(not(0)));
  }

  @Test
  public void ensureFullCommit() {
    dbClient.context().ensureFullCommit();
  }
}
