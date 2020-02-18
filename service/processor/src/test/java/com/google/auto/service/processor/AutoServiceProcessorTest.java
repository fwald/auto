/*
 * Copyright 2008 Google LLC
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
package com.google.auto.service.processor;

import static com.google.auto.service.processor.AutoServiceProcessor.MISSING_SERVICES_ERROR;
import static com.google.testing.compile.JavaSourcesSubject.assertThat;

import com.google.testing.compile.JavaFileObjects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.*;
import java.util.Set;

/**
 * Tests the {@link AutoServiceProcessor}.
 */
@RunWith(JUnit4.class)
public class AutoServiceProcessorTest {
  @Test
  public void autoService() {
      assertThat(
            JavaFileObjects.forResource("test/SomeService.java"),
            JavaFileObjects.forResource("test/SomeServiceProvider1.java"),
            JavaFileObjects.forResource("test/SomeServiceProvider2.java"),
            JavaFileObjects.forResource("test/Enclosing.java"),
            JavaFileObjects.forResource("test/AnotherService.java"),
            JavaFileObjects.forResource("test/AnotherServiceProvider.java"))
        .processedWith(new AutoServiceProcessor())
        .compilesWithoutError()
        .and().generatesFiles(
            JavaFileObjects.forResource("META-INF/services/test.SomeService"),
            JavaFileObjects.forResource("META-INF/services/test.AnotherService"));
  }

  @Test
  public void multiService() {
    assertThat(
            JavaFileObjects.forResource("test/SomeService.java"),
            JavaFileObjects.forResource("test/AnotherService.java"),
            JavaFileObjects.forResource("test/MultiServiceProvider.java"))
        .processedWith(new AutoServiceProcessor())
        .compilesWithoutError()
        .and().generatesFiles(
            JavaFileObjects.forResource("META-INF/services/test.SomeServiceMulti"),
            JavaFileObjects.forResource("META-INF/services/test.AnotherServiceMulti"));
  }

  @Test
  public void badMultiService() {
    assertThat(JavaFileObjects.forResource("test/NoServices.java"))
        .processedWith(new AutoServiceProcessor())
        .failsToCompile()
        .withErrorContaining(MISSING_SERVICES_ERROR);
  }

  //@ASSIGNMENT 3

  @Test
  public void FW_readServiceFileTest1() {
    //Test the case when the argument is not a valid input stream.
    // readServiceFile should throw an exception.
    try {
      ServicesFiles.readServiceFile(null);
    }
    catch (Exception exp){
      assert(true);
      return;
    }
    assert(false); // If we reach this, the test have failed.
  }

  @Test
  public void FW_readServiceFileTest2() {
  //Test the case where we have a valid input stream that reads from a file.
  // The method expects to read a Services file. Where each line contain a Service name.
  //We should get back a set of strings of the Service names

    try {
      String fName = "testfile.txt";
      FileWriter fw = new FileWriter(fName);
      String service1 = "Service1";
      String service2 = "Service2";

      fw.write(service1 + "\n");
      fw.write(service2);

      fw.flush();
      fw.close();

      File fo = new File(fName);
      Set<String> serviceFiles =  ServicesFiles.readServiceFile(new FileInputStream(fo)) ;
      assert(serviceFiles.size() == 2);
      assert(serviceFiles.contains(service1));
      assert(serviceFiles.contains(service2));

    }
    catch (Exception exp){
      //We need to catch exception since we are dealing with I/O. If this happens the test has failed though.
      assert(false);
    }
  }
  @Test
  public void FW_readServiceFileTest3() {
    //Test the case where we have a valid input stream that reads from a file.
    // The method expects to read a Services file. Where each line contain a Service name.
    //Lines can also include comments, indicated with a '#'. All text after this line should be removed

    try {
      String fName = "testfile.txt";
      FileWriter fw = new FileWriter(fName);

      String service1 = "Service1";
      String service2 = "Service2";

      String line1 = service1 + "#With some added comments!";
      String line2 = "#Here we have a comment!\n" + service2;
      fw.write(line1);
      fw.write(line2);

      fw.flush();
      fw.close();

      File fo = new File(fName);
      Set<String> serviceFiles =  ServicesFiles.readServiceFile(new FileInputStream(fo)) ;
      assert(serviceFiles.size() == 2);
      assert(serviceFiles.contains("Service1"));
      assert(serviceFiles.contains("Service2"));

    }
    catch (Exception exp){
      //We need to catch exception since we are dealing with I/O. If this happens the test has failed though.
      assert(false);
    }
  }

  @Test
  public void FW_readServiceFileTest4() {
    //Test the case where we have a valid input stream that reads from a file.
    // The file is empty though.
    //We should get back an empty Set of Strings.

    try {
      String fName = "testfile2.txt";
      FileWriter fw = new FileWriter(fName);
      fw.flush();
      fw.close();

      File fo = new File(fName);
      Set<String> serviceFiles =  ServicesFiles.readServiceFile(new FileInputStream(fo)) ;
      assert(serviceFiles.size() == 0);
    }
    catch (IOException exp){
      //We need to catch exception since we are dealing with I/O. If this happens the test has failed though.
      assert(false);
    }
  }
}
