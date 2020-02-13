package com.google.auto.value.processor;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;

import java.io.PrintWriter;

public class AdrianInstrument extends RunListener {
    public static boolean[] defineVars;
    public static boolean[] tokenEnd;

  public void testRunStarted(Description description) throws Exception {
      System.out.println("AdrianInstrument: testRunStarted!");
      defineVars = new boolean[12];
      tokenEnd = new boolean[12];
  }

  public void testRunFinished(Result result) throws Exception {
      System.out.println("AdrianInstrument: testRunFinished!");
      System.out.print("AdrianInstrument: defineVars: ");
      double res1 = 0;
      for(int i = 0; i < defineVars.length; i++){
          System.out.print(defineVars[i] + " ");
          if(defineVars[i])
              res1++;
      }
      System.out.print("\n");
      System.out.println("AdrianInstrument: defineVars branch coverage: " +  res1 / defineVars.length);

      System.out.print("AdrianInstrument: tokenEnd: ");
      double res2 = 0;
      for(int i = 0; i < tokenEnd.length; i++){
          System.out.print(tokenEnd[i] + " ");
          if(tokenEnd[i])
              res2++;
      }
      System.out.print("\n");
      System.out.println("AdrianInstrument: tokenEnd branch coverage: " +  res2 / tokenEnd.length);
  }
}