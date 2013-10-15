package com.adobe.epubcheck.test;

import com.adobe.epubcheck.tool.Checker;
import com.adobe.epubcheck.util.Messages;
import com.adobe.epubcheck.util.outWriter;
import junit.framework.Assert;
import org.custommonkey.xmlunit.DetailedDiff;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.ElementNameAndTextQualifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class common
{
  public static void runExpTest(String componentName, String testName, int expectedReturnCode, boolean isJson)
  {
    runExpTest(componentName, testName, expectedReturnCode, isJson, new String[0]);
  }

  public static void runExpTest(String componentName, String testName, int expectedReturnCode, boolean isJson, String... extraArgs)
  {

    String extension = isJson ? "json" : "xml";
    int extraArgsLength = extraArgs != null ? extraArgs.length : 0;
    String[] args = new String[6 + extraArgsLength];
    URL inputUrl = common.class.getResource(componentName + "/" + testName);
    Assert.assertNotNull("Input folder is missing.", inputUrl);
    String inputPath = inputUrl.getPath();
    String outputPath = inputPath + "/../" + testName + "_actual_results." + extension;

    args[0] = inputPath;
    args[1] = "-mode";
    args[2] = "exp";
    args[3] = (isJson) ? "-j" : "-o";
    args[4] = outputPath;
    args[5] = "-u";
    for (int i = 0; i < extraArgsLength; ++i)
    {
      args[6+i] = extraArgs[i];
    }

    runCustomTest(componentName, testName, expectedReturnCode, args);
    File actualOutput = new File(outputPath);
    Assert.assertTrue("Output file is missing.", actualOutput.exists());
    URL expectedUrl = common.class.getResource(componentName + "/" + testName + "_expected_results." + extension);
    Assert.assertNotNull("Expected file is missing.", expectedUrl);
    File expectedOutput = new File(expectedUrl.getPath());
    Assert.assertTrue("Expected file is missing.", expectedOutput.exists());
    if (isJson)
    {
      compareJson(expectedOutput, actualOutput);
    }
    else
    {
      compareXml(expectedOutput, actualOutput);
    }
    File tempFile = new File(testName + ".epub");
    Assert.assertFalse("Temp file left over after test: " + tempFile.getPath(), tempFile.exists());
  }

  public static void runCustomTest(String componentName, String testName, int expectedReturnCode, String... args)
  {
    runCustomTest(componentName, testName, expectedReturnCode, false, args);
  }

  public static void runCustomTest(String componentName, String testName, int expectedReturnCode, boolean quiet, String... args)
  {
    try
    {
      if (!quiet)
      {
        outWriter.printf("Start %s test('%s')\n", componentName, testName);
      }
      int result = Integer.MAX_VALUE;
      try
      {
        Checker.main(args);
      }
      catch (NoExitSecurityManager.ExitException e)
      {
        result = e.status;
      }

      Assert.assertEquals("Return code", expectedReturnCode, result);

    }
    catch (Exception ex)
    {
      System.err.println(Messages.THERE_WERE_ERRORS);
      ex.printStackTrace();
      Assert.assertTrue(String.format("Error running %s test('%s')", componentName, testName), false);
    }
    if (!quiet)
    {
      outWriter.printf("Completed %s test('%s')\n", componentName, testName);
    }
  }

  public static void compareText(File expectedOutput, File actualOutput) throws Exception
  {
    BufferedReader expectedReader = new BufferedReader(new FileReader(expectedOutput));
    BufferedReader actualReader = new BufferedReader(new FileReader(actualOutput));
    String expectedLine = expectedReader.readLine();
    while (expectedLine != null)
    {
      String actualLine = actualReader.readLine();
      Assert.assertNotNull("Expected: " + expectedLine + " Actual: null", actualLine);
      actualLine = actualLine.trim();
      expectedLine = expectedLine.trim();
      Assert.assertEquals("Expected: " + expectedLine + " Actual: " + actualLine, expectedLine, actualLine);
      expectedLine = expectedReader.readLine();
    }
    String overflow = actualReader.readLine();
    Assert.assertNull("Expected: null Actual: " + overflow, overflow);
    expectedReader.close();
    actualReader.close();
  }

  public static void compareJson(File expectedOutput, File actualOutput)
  {
    ArrayList<String> ignoreFields = new ArrayList<String>();
    ignoreFields.add("customMessageFileName");
    ignoreFields.add("/checker/checkDate");
    ignoreFields.add("/checker/checkerVersion");
    ignoreFields.add("/checker/elapsedTime");
    ignoreFields.add("/checker/path");
    try
    {
      jsonCompare.compareJsonFiles(expectedOutput, actualOutput, ignoreFields);
    }
    catch (Exception ex)
    {
      System.err.println(Messages.THERE_WERE_ERRORS);
      ex.printStackTrace();
      Assert.assertTrue("Error performing the json comparison: ", false);
    }
  }

  public static void compareXml(File expectedOutput, File actualOutput)
  {
    Diff diff;
    try
    {
      FileReader expectedReader = new FileReader(expectedOutput);
      FileReader actualReader = new FileReader(actualOutput);
      diff = new Diff(expectedReader, actualReader);
    }
    catch (Exception ex)
    {
      System.err.println(Messages.THERE_WERE_ERRORS);
      ex.printStackTrace();
      Assert.assertTrue("Error performing the json comparison: ", false);
      return;
    }
    OutputDifferenceListener listener = new OutputDifferenceListener();
    diff.overrideDifferenceListener(listener);
    diff.overrideElementQualifier(new ElementNameAndTextQualifier());
    Assert.assertTrue("There were skipped comparisons.", listener.getSkippedComparisons() == 0);
    if (!diff.similar())
    {
      DetailedDiff details = new DetailedDiff(diff);
      List differences = details.getAllDifferences();
      StringBuilder sb = new StringBuilder();
      for (Object difference : differences)
      {
        sb.append(difference.toString());
      }

      Assert.assertTrue("The expected xml was different: " + sb.toString(), diff.similar());
    }
  }
}
