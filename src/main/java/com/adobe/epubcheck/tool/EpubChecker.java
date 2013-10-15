/*
 * Copyright (c) 2007 Adobe Systems Incorporated
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of
 *  this software and associated documentation files (the "Software"), to deal in
 *  the Software without restriction, including without limitation the rights to
 *  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *  the Software, and to permit persons to whom the Software is furnished to do so,
 *  subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *    <AdobeIP#0000474>
 */

package com.adobe.epubcheck.tool;

import com.adobe.epubcheck.api.EpubCheck;
import com.adobe.epubcheck.api.EpubCheckFactory;
import com.adobe.epubcheck.api.Report;
import com.adobe.epubcheck.nav.NavCheckerFactory;
import com.adobe.epubcheck.opf.DocumentValidator;
import com.adobe.epubcheck.opf.DocumentValidatorFactory;
import com.adobe.epubcheck.opf.OPFCheckerFactory;
import com.adobe.epubcheck.ops.OPSCheckerFactory;
import com.adobe.epubcheck.overlay.OverlayCheckerFactory;
import com.adobe.epubcheck.reporting.CheckingReport;
import com.adobe.epubcheck.util.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

public class EpubChecker
{

  String path = null;
  String mode = null;
  EPUBVersion version = EPUBVersion.VERSION_3;
  boolean expanded = false;
  boolean keep = false;
  boolean jsonOutput = false;
  boolean xmlOutput = false;
  File fileOut;
  File listChecksOut;
  File customMessageFile;
  boolean listChecks = false;
  boolean useCustomMessageFile = false;
  boolean failOnWarnings = false;

  int reportingLevel = ReportingLevel.Info;

  private static final HashMap<OPSType, String> modeMimeTypeMap;

  static
  {
    HashMap<OPSType, String> map = new HashMap<OPSType, String>();

    map.put(new OPSType("xhtml", EPUBVersion.VERSION_2), "application/xhtml+xml");
    map.put(new OPSType("xhtml", EPUBVersion.VERSION_3), "application/xhtml+xml");

    map.put(new OPSType("svg", EPUBVersion.VERSION_2), "image/svg+xml");
    map.put(new OPSType("svg", EPUBVersion.VERSION_3), "image/svg+xml");

    map.put(new OPSType("mo", EPUBVersion.VERSION_3), "application/smil+xml");
    map.put(new OPSType("nav", EPUBVersion.VERSION_3), "nav");
    modeMimeTypeMap = map;
  }

  private static final HashMap<OPSType, DocumentValidatorFactory> documentValidatorFactoryMap;
  private static final String E_PUB_CHECK_CUSTOM_MESSAGE_FILE = "ePubCheckCustomMessageFile";

  static
  {
    HashMap<OPSType, DocumentValidatorFactory> map = new HashMap<OPSType, DocumentValidatorFactory>();
    map.put(new OPSType(null, EPUBVersion.VERSION_2), EpubCheckFactory.getInstance());
    map.put(new OPSType(null, EPUBVersion.VERSION_3), EpubCheckFactory.getInstance());
    map.put(new OPSType("opf", EPUBVersion.VERSION_2), OPFCheckerFactory.getInstance());
    map.put(new OPSType("opf", EPUBVersion.VERSION_3), OPFCheckerFactory.getInstance());
    map.put(new OPSType("xhtml", EPUBVersion.VERSION_2), OPSCheckerFactory.getInstance());
    map.put(new OPSType("xhtml", EPUBVersion.VERSION_3), OPSCheckerFactory.getInstance());
    map.put(new OPSType("svg", EPUBVersion.VERSION_2), OPSCheckerFactory.getInstance());
    map.put(new OPSType("svg", EPUBVersion.VERSION_3), OPSCheckerFactory.getInstance());
    map.put(new OPSType("mo", EPUBVersion.VERSION_3), OverlayCheckerFactory.getInstance());
    map.put(new OPSType("nav", EPUBVersion.VERSION_3), NavCheckerFactory.getInstance());

    documentValidatorFactoryMap = map;
  }

  int validateFile(String path, EPUBVersion version, Report report)
  {
    GenericResourceProvider resourceProvider;

    if (path.startsWith("http://") || path.startsWith("https://"))
    {
      resourceProvider = new URLResourceProvider(path);
    }
    else
    {
      File f = new File(path);
      if (f.exists())
      {
        resourceProvider = new FileResourceProvider(path);
      }
      else
      {
        System.err.println(String.format("File not found: '%1$s'", path));
        return 1;
      }
    }

    OPSType opsType = new OPSType(mode, version);

    DocumentValidatorFactory factory = documentValidatorFactoryMap.get(opsType);

    if (factory == null)
    {
      outWriter.println(Messages.DISPLAY_HELP);
      System.err.println(String.format(
          Messages.MODE_VERSION_NOT_SUPPORTED, mode, version));

      throw new RuntimeException(String.format(Messages.MODE_VERSION_NOT_SUPPORTED, mode, version));
    }

    DocumentValidator check = factory.newInstance(report, path,
        resourceProvider, modeMimeTypeMap.get(opsType),
        version);
    if (check.getClass() == EpubCheck.class)
    {
      int validationResult = ((EpubCheck)check).doValidate();
      if (validationResult == 0)
      {
        outWriter.println(Messages.NO_ERRORS__OR_WARNINGS);
        return 0;
      }
      else if (validationResult == 1)
      {
        System.err.println(Messages.THERE_WERE_WARNINGS);
        return failOnWarnings ? 1 : 0;
      }
      System.err.println(Messages.THERE_WERE_ERRORS);
      return 1;
    }
    else
    {
      if (check.validate())
      {
        outWriter.println(Messages.NO_ERRORS__OR_WARNINGS);
        return 0;
      }
      System.err.println(Messages.THERE_WERE_ERRORS);
    }
    return 1;
  }


  int validateEpubFile(String path, EPUBVersion version, Report report)
  {
    GenericResourceProvider resourceProvider;

    if (path.startsWith("http://") || path.startsWith("https://"))
    {
      resourceProvider = new URLResourceProvider(path);
    }
    else
    {
      File f = new File(path);
      if (f.exists())
      {
        resourceProvider = new FileResourceProvider(path);
      }
      else
      {
        System.err.println(String.format("File not found: '%1$s'", path));
        return 1;
      }
    }

    OPSType opsType = new OPSType(mode, version);

    DocumentValidatorFactory factory = documentValidatorFactoryMap.get(opsType);

    if (factory == null)
    {
      outWriter.println(Messages.DISPLAY_HELP);
      System.err.println(String.format(
          Messages.MODE_VERSION_NOT_SUPPORTED, mode, version));

      throw new RuntimeException(String.format(Messages.MODE_VERSION_NOT_SUPPORTED, mode, version));
    }

    DocumentValidator check = factory.newInstance(report, path,
        resourceProvider, modeMimeTypeMap.get(opsType),
        version);

    if (check.validate())
    {
      outWriter.println(Messages.NO_ERRORS__OR_WARNINGS);
      return 0;
    }
    System.err.println(Messages.THERE_WERE_ERRORS);

    return 1;
  }


  public int run(String[] args)
  {
    int returnValue = 1;
    try
    {
      if (processArguments(args))
      {
        Report report = createReport();
        report.initialize();
        if (listChecks)
        {
          dumpMessageDictionary(report);
          return 0;
        }
        if (useCustomMessageFile)
        {
          report.setCustomMessageFile(customMessageFile.getAbsolutePath());
        }
        returnValue = processFile(report);
        int returnValue2 = report.generate();
        if (returnValue == 0)
        {
          returnValue = returnValue2;
        }
      }
    }
    catch (Exception ignored)
    {
      returnValue = 1;
    }
    finally
    {
      outWriter.println("epubcheck completed");
      outWriter.setQuiet(false);
    }
    return returnValue;
  }

  private void dumpMessageDictionary(Report report) throws
      IOException
  {
    OutputStreamWriter fw = null;
    try
    {
      if (listChecksOut != null)
      {
        fw = new FileWriter(listChecksOut);
      }
      else
      {
        fw = new OutputStreamWriter(System.out);
      }
      report.getDictionary().dumpMessages(fw);
    }
    catch (Exception e)
    {
      if (listChecksOut != null)
      {
        System.err.println(String.format("Error creating config file '%1$s'.", listChecksOut.getAbsoluteFile()));
      }
      System.err.println(e.getMessage());
    }
    finally
    {
      if (fw != null)
      {
        try
        {
          fw.close();
        }
        catch (IOException ignored)
        {
        }
      }
    }
  }

  private Report createReport() throws
      IOException
  {
    Report report;
    if (listChecks)
    {
      report = new DefaultReportImpl("none");
    }
    else if (jsonOutput)
    {
      report = new CheckingReport(path, fileOut.getPath());
    }
    else if (xmlOutput)
    {
      report = new XmlReportImpl(fileOut, path, EpubCheck.version());
    }
    else
    {
      report = new DefaultReportImpl(path);
    }
    report.setReportingLevel(this.reportingLevel);
    if (useCustomMessageFile)
    {
      report.setOverrideFile(customMessageFile);
    }

    return report;
  }

  public int processEpubFile(String[] args)
  {
    int returnValue = 1;
    try
    {
      if (processArguments(args))
      {
        Report report = createReport();
        report.initialize();
        if (listChecks)
        {
          dumpMessageDictionary(report);
          return 0;
        }
        if (useCustomMessageFile)
        {
          report.setCustomMessageFile(customMessageFile.getAbsolutePath());
        }
        returnValue = processEpubFile(report);
        int returnValue2 = report.generate();
        if (returnValue == 0)
        {
          returnValue = returnValue2;
        }
      }
    }
    catch (Exception ignored)
    {
      returnValue = 1;
    }
    finally
    {
      outWriter.println("epubcheck completed");
      outWriter.setQuiet(false);
    }
    return returnValue;
  }

  int processEpubFile(Report report)
  {
    report.info(null, FeatureEnum.TOOL_NAME, "epubcheck");
    report.info(null, FeatureEnum.TOOL_VERSION, EpubCheck.version());
    int result;

    try
    {
      if (!expanded)
      {
        if (mode != null)
        {
          report.info(null, FeatureEnum.EXEC_MODE, String.format(Messages.SINGLE_FILE, mode, version.toString()));
        }
        result = validateFile(path, version, report);
      }
      else
      {
        System.err.println(Messages.ERROR_PROCESSING_UNEXPANDED_EPUB);
        return 1;
      }

      return result;
    }
    catch (Throwable e)
    {
      e.printStackTrace();
      return 1;
    }
    finally
    {
      report.close();
    }
  }

  private int processFile(Report report)
  {
    report.info(null, FeatureEnum.TOOL_NAME, "epubcheck");
    report.info(null, FeatureEnum.TOOL_VERSION, EpubCheck.version());
    int result = 0;

    try
    {
      if (expanded)
      {
        Archive epub;

        try
        {
          epub = new Archive(path, true);
        }
        catch (RuntimeException ex)
        {
          System.err.println(Messages.THERE_WERE_ERRORS);
          return 1;
        }

        epub.createArchive();
        report.setEpubFileName(epub.getEpubFile().getAbsolutePath());
        EpubCheck check = new EpubCheck(epub.getEpubFile(), report);
        int validationResult = check.doValidate();
        if (validationResult == 0)
        {
          outWriter.println(Messages.NO_ERRORS__OR_WARNINGS);
          result = 0;
        }
        else if (validationResult == 1)
        {
          System.err.println(Messages.THERE_WERE_WARNINGS);
          result = failOnWarnings ? 1 : 0;
        }
        else if (validationResult >= 2)
        {
          System.err.println(Messages.THERE_WERE_ERRORS);
          result = 1;
        }

        if (keep)
        {
          if ((report.getErrorCount() > 0) || (report.getFatalErrorCount() > 0))
          {
            //keep if valid or only warnings
            System.err.println(Messages.DELETING_ARCHIVE);
            epub.deleteEpubFile();
          }
        }
        else
        {
          epub.deleteEpubFile();
        }
      }
      else
      {
        if (mode != null)
        {
          report.info(null, FeatureEnum.EXEC_MODE, String.format(Messages.SINGLE_FILE, mode, version.toString()));
        }
        result = validateFile(path, version, report);
      }

      return result;
    }
    catch (Throwable e)
    {
      e.printStackTrace();
      return 1;
    }
    finally
    {
      report.close();
    }
  }

  /**
   * This method iterates through all of the arguments passed to main to find
   * accepted flags and the name of the file to check. This method returns the
   * last argument that ends with ".epub" (which is assumed to be the file to
   * check) Here are the currently accepted flags: <br>
   * <br>
   * -? or -help = display usage instructions <br>
   * -v or -version = display tool version number
   *
   * @param args String[] containing arguments passed to main
   * @return the name of the file to check
   */
  private boolean processArguments(String[] args)
  {
    //displayVersion();
    // Exit if there are no arguments passed to main
    if (args.length < 1)
    {
      System.err.println(Messages.ARGUMENT_NEEDED);
      return false;
    }

    setCustomMessageFileFromEnvironment();

    for (int i = 0; i < args.length; i++)
    {
      if (args[i].equals("--version") || args[i].equals("-version") || args[i].equals("-v"))
      {
        if (i + 1 < args.length)
        {
          ++i;
          if (args[i].equals("2.0") || args[i].equals("2"))
          {
            version = EPUBVersion.VERSION_2;
          }
          else if (args[i].equals("3.0") || args[i].equals("3"))
          {
            version = EPUBVersion.VERSION_3;
          }
          else
          {
            outWriter.println(Messages.DISPLAY_HELP);
            throw new RuntimeException(new InvalidVersionException(
                InvalidVersionException.UNSUPPORTED_VERSION));
          }
        }
        else
        {
          outWriter.println(Messages.DISPLAY_HELP);
          throw new RuntimeException(Messages.VERSION_ARGUMENT_EXPECTED);
        }
      }
      else if (args[i].equals("--mode") || args[i].equals("-mode") || args[i].equals("-m"))
      {
        if (i + 1 < args.length)
        {
          mode = args[++i];
          expanded = mode.equals("exp");
        }
        else
        {
          outWriter.println(Messages.DISPLAY_HELP);
          throw new RuntimeException(Messages.MODE_ARGUMENT_EXPECTED);
        }
      }
      else if (args[i].equals("--save") || args[i].equals("-save") || args[i].equals("-s"))
      {
        keep = true;
      }
      else if (args[i].equals("--out") || args[i].equals("-out") || args[i].equals("-o"))
      {
        if ((args.length > (i + 1)) && !(args[i+1].startsWith("-")))
        {
          fileOut = new File(args[++i]);
        }
        else
        {
          fileOut = new File(path + "check.xml");
        }
        xmlOutput = true;
      }
      else if (args[i].equals("--json") || args[i].equals("-json") || args[i].equals("-j"))
      {
        if ((args.length > (i + 1)) && !(args[i+1].startsWith("-")))
        {
          fileOut = new File(args[++i]);
        }
        else
        {
          fileOut = new File(path + "check.json");
        }
        jsonOutput = true;
      }
      else if (args[i].equals("--info") || args[i].equals("-i"))
      {
        reportingLevel = ReportingLevel.Info;
      }
      else if (args[i].equals("--fatal") || args[i].equals("-f"))
      {
        reportingLevel = ReportingLevel.Fatal;
      }
      else if (args[i].equals("--error") || args[i].equals("-e"))
      {
        reportingLevel = ReportingLevel.Error;
      }
      else if (args[i].equals("--warn") || args[i].equals("-w"))
      {
        reportingLevel = ReportingLevel.Warning;
      }
      else if (args[i].equals("--usage") || args[i].equals("-u"))
      {
        reportingLevel = ReportingLevel.Usage;
      }
      else if (args[i].equals("--quiet") || args[i].equals("-q"))
      {
        outWriter.setQuiet(true);
      }
      else if (args[i].startsWith("--failonwarnings"))
      {
        String fw = args[i].substring("--failonwarnings".length());
        failOnWarnings = (fw.compareTo("-") != 0);
      }
      else if (args[i].equals("--redir") || args[i].equals("-r"))
      {
        if (i + 1 < args.length)
        {
          fileOut = new File(args[++i]);
        }
      }
      else if (args[i].equals("--customMessages") || args[i].equals("-c"))
      {
        if (i + 1 < args.length)
        {
          String fileName = args[i+1];
          if ("none".compareTo(fileName.toLowerCase()) == 0)
          {
            customMessageFile = null;
            useCustomMessageFile = false;
            ++i;
          }
          else if (!fileName.startsWith("-"))
          {
            customMessageFile = new File(fileName);
            useCustomMessageFile = true;
            ++i;
          }
          else
          {
            System.err.println("Expected the Custom message file name, but found '" +  fileName + "'");
            displayHelp();
            return false;
          }
        }
      }
      else if (args[i].equals("--listChecks") || args[i].equals("-l"))
      {
        if (i + 1 < args.length)
        {
          if (!args[i+1].startsWith("-"))
          {
            listChecksOut = new File(args[++i]);
          }
          else
          {
            listChecksOut = null;
          }
        }
        listChecks = true;
      }
      else if (args[i].equals("--help") || args[i].equals("-help") || args[i].equals("-h") || args[i].equals("-?"))
      {
        displayHelp(); // display help message
      }
      else
      {
        if (path == null)
        {
          path = args[i];
        }
        else
        {
          System.err.println("Unrecognized argument: '" + args[i] + "'");
          displayHelp();
          return false;
        }
      }
    }

    if (xmlOutput && jsonOutput)
    {
      System.err.println(Messages.OUTPUT_TYPE_CONFLICT);
      return false;
    }
    if (path != null)
    {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < path.length(); i++)
      {
        if (path.charAt(i) == '\\')
        {
          sb.append('/');
        }
        else
        {
          sb.append(path.charAt(i));
        }
      }
      path = sb.toString();
    }

    if (path == null)
    {
      if (listChecks)
      {
        return true;
      }
      else
      {
        System.err.println(Messages.NO_FILE_SPECIFIED);
        return false;
      }
    }
    else if (path.matches(".+\\.[Ee][Pp][Uu][Bb]"))
    {
      if (mode != null || version != EPUBVersion.VERSION_3)
      {
        System.err.println(Messages.MODE_VERSION_IGNORED);
        mode = null;
      }
    }
    else if (mode == null)
    {
      outWriter.println(Messages.MODE_REQUIRED);
      return false;
    }
    return true;
  }

  private void setCustomMessageFileFromEnvironment()
  {
    Map<String, String> env = System.getenv();
    String customMessageFileName = env.get(E_PUB_CHECK_CUSTOM_MESSAGE_FILE);
    if (customMessageFileName != null && customMessageFileName.length() > 0)
    {
      File f = new File(customMessageFileName);
      if (f.exists())
      {
        customMessageFile = f;
      }
    }
  }

  /**
   * This method displays a short help message that describes the command-line
   * usage of this tool
   */
  private static void displayHelp()
  {
    outWriter.println(Messages.HELP_TEXT);
  }

  private static void displayVersion()
  {
    System.err.println("Epubcheck Version " + EpubCheck.version() + "\n");
  }
}
