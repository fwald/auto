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
        int sum = 0;
        for(int i = 0; i<reportMissingElementsBranches.length; i++) {
            System.out.println("iZafiroInstrument - reportMissingElements: branch "+ i + " status " + reportMissingElementsBranches[i]);
            sum += reportMissingElementsBranches[i];
        }
        System.out.println("iZafiroInstrument - reportMissingElements: " + sum + "/" + reportMissingElementsBranches.length);

        sum = 0;
        for(int i = 0; i<validElementsBranches.length; i++) {
            System.out.println("iZafiroInstrument - validElementsBranches: branch "+ i + " status " + validElementsBranches[i]);
            sum += validElementsBranches[i];
        }
        System.out.println("iZafiroInstrument - validElementsBranches: " + sum + "/" + validElementsBranches.length);
    }

}