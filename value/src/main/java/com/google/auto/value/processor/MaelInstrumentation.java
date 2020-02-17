package com.google.auto.value.processor;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;

public class MaelInstrumentation extends RunListener {
    public static int NUM_BRANCHES = 11;
    public static boolean [] branchCoverage = new boolean[NUM_BRANCHES];
    public static int NUM_BRANCHES_2 = 13;
    public static boolean [] branchCoverage2 = new boolean[NUM_BRANCHES_2];

  public void testRunStarted(Description description) throws Exception {
      for (int i = 0; i < NUM_BRANCHES; i++) {
          branchCoverage[i] = false;
      }
      for (int i = 0; i < NUM_BRANCHES_2; i++) {
          branchCoverage2[i] = false;
      }
  }

  public void testRunFinished(Result result) throws Exception {

    PrintWriter writer = new PrintWriter(new BufferedWriter( new FileWriter("maelInstru.txt"))); 
    // Print for monitored method 1
    String methodName = "BuilderMethodClassifier.classifyMethods()";

    writer.println("Coverage Test for method " + methodName);
    writer.println("Method has in total " + NUM_BRANCHES + " branches");
    writer.println("Results:");

    int countCoverage = 0;
    for ( int i = 0; i < NUM_BRANCHES; i++ ){
        writer.println("Branch with id: " + i + " " + "was covered by test: " + branchCoverage[i] );
        countCoverage += branchCoverage[i] ? 1 : 0 ; 
    }
    float ratio = ((float) countCoverage) / ((float)  NUM_BRANCHES);
    ratio *= 100.0;

    writer.println("Total branch coverage: " + ratio + "%");

    // Print for monitored method 2
    methodName = "PropertyBuilderClassifier.makePropertyBuilder()";

    writer.println("Coverage Test for method " + methodName);
    writer.println("Method has in total " + NUM_BRANCHES_2 + " branches");
    writer.println("Results:");

    countCoverage = 0;

    for ( int i = 0; i < NUM_BRANCHES_2; i++ ){
        writer.println("Branch with id: " + i + " " + "was covered by test: " + branchCoverage2[i] );
        countCoverage += branchCoverage2[i] ? 1 : 0 ; 
    }

    ratio = ((float) countCoverage) / ((float)  NUM_BRANCHES_2);
    ratio *= 100.0;

    writer.println("Total branch coverage: " + ratio + "%");

    writer.flush();
    writer.close();

  }
}