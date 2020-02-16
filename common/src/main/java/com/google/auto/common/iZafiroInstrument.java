package com.google.auto.common;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;

public class iZafiroInstrument extends RunListener {
    public static int[] reportMissingElementsBranches = new int[10];
    public static int[] validElementsBranches = new int[15];

    public void testRunStarted(Description description) throws Exception {
    }

    public void testRunFinished(Result result) throws Exception {
        System.out.println("iZafiroInstrument - reportMissingElements: " + arraySum(reportMissingElementsBranches) + "/" + reportMissingElementsBranches.length);
        System.out.println("iZafiroInstrument - validElementsBranches: " + arraySum(validElementsBranches) + "/" + validElementsBranches.length);
    }

    public static int arraySum(int[] array) {
        int sum = 0;
        for(int x : array) {
            sum += x;
        }
        return sum;
    }
}