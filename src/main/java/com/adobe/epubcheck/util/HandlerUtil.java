package com.adobe.epubcheck.util;

import com.adobe.epubcheck.api.Report;
import com.adobe.epubcheck.messages.MessageId;
import com.adobe.epubcheck.messages.MessageLocation;
import com.adobe.epubcheck.xml.XMLParser;

import java.util.HashSet;

public class HandlerUtil
{

  public static void processPrefixes(String prefix,
      HashSet<String> prefixSet, Report report, String path, int line,
      int column)
  {
    if (prefix == null)
    {
      return;
    }
    prefix = prefix.replaceAll("[\\s]+", " ");

    String prefixArray[] = prefix.split(" ");
    boolean validPrefix;
    for (int i = 0; i < prefixArray.length; i++)
    {
      validPrefix = true;
      if (!prefixArray[i].endsWith(":"))
      {
        report.message(MessageId.OPF_004, new MessageLocation(path, line, column), prefixArray[i]);
        validPrefix = false;
      }
      if (i + 1 >= prefixArray.length)
      {
        report.message(MessageId.OPF_005, new MessageLocation(path, line, column), prefixArray[i]);
        return;
      }
      i++;
      if (!prefixArray[i].startsWith("http://"))
      {
        report.message(MessageId.OPF_006, new MessageLocation(path, line, column), prefixArray[i - 1]);
      }
      else if (validPrefix)
      {
        if (!prefixSet.contains(prefixArray[i - 1].substring(0,
            prefixArray[i - 1].length() - 1)))
        {
          prefixSet.add(prefixArray[i - 1].substring(0,
              prefixArray[i - 1].length() - 1));
        }
        else
        {
          report.message(MessageId.OPF_007,
              new MessageLocation(path, line, column),
              prefixArray[i - 1].substring(0, prefixArray[i - 1].length() - 1));
        }
      }
    }

  }

  public static boolean checkXMLVersion(XMLParser parser)
  {
    String version = parser.getXMLVersion();

    //I don't think it is possible for this to be null.  A null version would cause a SAX parser error.
    if (version == null)
    {
      parser.getReport().message(MessageId.HTM_002, new MessageLocation(parser.getResourceName(),
          parser.getLineNumber(), parser.getColumnNumber()));
      return true;
    }

    if (!"1.0".equals(version))
    {
      parser.getReport().message(MessageId.HTM_001, new MessageLocation(parser.getResourceName(),
          parser.getLineNumber(), parser.getColumnNumber()), version);
      return true;
    }
    return false;
  }
}
