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
      int sum = arraySum(canAssignStaticMethodResultBranches);
      System.out.print("RuwaidInstrument - canAssignStaticMethodResult: " + sum + "/" + canAssignStaticMethodResultBranches.length);
      if(sum != canAssignStaticMethodResultBranches.length) {
          System.out.println(" - Missing Branches: " + printMissing(canAssignStaticMethodResultBranches));
      } else {
          System.out.println();
      }

      sum = arraySum(classifyMethodOneArgBranches);
      System.out.print("RuwaidInstrument - classifyMethodOneArg: " + sum + "/" + classifyMethodOneArgBranches.length);
      if(sum != classifyMethodOneArgBranches.length) {
          System.out.println(" - Missing Branches: " + printMissing(classifyMethodOneArgBranches));
      } else {
          System.out.println();
      }
  }

  public String printMissing(int[] array) {
      StringBuilder ret = new StringBuilder();
      for (int i = 0; i < array.length; i++) {
          if(array[i] == 0) {
              ret.append(i).append(",");
          }
      }
      return ret.toString();
  }

  public static int arraySum(int[] array) {
    int sum = 0;
    for(int x : array) {
      sum += x;
    }
    return sum;
  }
} 