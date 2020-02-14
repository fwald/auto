package com.google.auto.value.processor;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;

import java.io.PrintWriter;

public class RuwaidInstrument extends RunListener {
    public static int[] canAssignStaticMethodResultBranches = new int[10];
    public static int[] classifyMethodOneArgBranches = new int[12];

  public void testRunStarted(Description description) throws Exception {
  }

  public void testRunFinished(Result result) throws Exception {
      System.out.println("RuwaidInstrument - canAssignStaticMethodResult: " + arraySum(canAssignStaticMethodResultBranches) + "/" + canAssignStaticMethodResultBranches.length);
      System.out.println("RuwaidInstrument - classifyMethodOneArg: " + arraySum(classifyMethodOneArgBranches) + "/" + classifyMethodOneArgBranches.length);
  }

  public static int arraySum(int[] array) {
    int sum = 0;
    for(int x : array) {
      sum += x;
    }
    return sum;
  }
} 