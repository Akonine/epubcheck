package com.adobe.epubcheck.css;

import com.adobe.epubcheck.api.Report;
import com.adobe.epubcheck.messages.MessageId;
import com.adobe.epubcheck.messages.MessageLocation;
import com.adobe.epubcheck.opf.OPFChecker;
import com.adobe.epubcheck.opf.OPFChecker30;
import com.adobe.epubcheck.opf.XRefChecker;
import com.adobe.epubcheck.util.EPUBVersion;
import com.adobe.epubcheck.util.FeatureEnum;
import com.adobe.epubcheck.util.PathUtil;
import com.google.common.base.CharMatcher;
import org.idpf.epubcheck.util.css.CssContentHandler;
import org.idpf.epubcheck.util.css.CssErrorHandler;
import org.idpf.epubcheck.util.css.CssExceptions.CssException;
import org.idpf.epubcheck.util.css.CssGrammar.*;
import org.idpf.epubcheck.util.css.CssLocation;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CSSHandler implements CssContentHandler, CssErrorHandler
{
  final String path;
  final XRefChecker xrefChecker;
  final Report report;
  final EPUBVersion version;
  int startingLineNumber = 0; //append to line info from css parser
  int startingColumnNumber = 0;
  static final CharMatcher SPACE_AND_QUOTES = CharMatcher.anyOf(" \t\n\r\f\"'").precomputed();

  //vars for font-face info
  String fontFamily;
  String fontStyle;
  String fontWeight;
  String fontUri;
  boolean inFontFace = false;
  boolean hasFontFaceDeclarations = false;
  boolean inKeyFrames = false;
  CssAtRule atRule = null;

  public CSSHandler(String path, XRefChecker xrefChecker, Report report,
      EPUBVersion version)
  {
    this.path = path;
    this.xrefChecker = xrefChecker;
    this.report = report;
    this.version = version;
  }

  private MessageLocation getCorrectedMessageLocation(String fileName, int lineNumber, int columnNumber, String context)
  {
    lineNumber = correctedLineNumber(lineNumber);
    columnNumber = correctedColumnNumber(lineNumber, columnNumber);
    return new MessageLocation(fileName, lineNumber, columnNumber, context);
  }

  private int correctedLineNumber(int lineNumber)
  {
    return startingLineNumber + lineNumber;
  }

  private int correctedColumnNumber(int lineNumber, int columnNumber)
  {
    if (lineNumber != 0)
    {
      return columnNumber;
    }
    return startingColumnNumber + columnNumber;
  }


  static final Pattern invalidTokenStringFinder = Pattern.compile("Token '[0-9]+%' not allowed here");

  @Override
  public void error(CssException e) throws
      CssException
  {
    String message = e.getMessage();

    if (inKeyFrames)
    {
      Matcher m = invalidTokenStringFinder.matcher(message);
      if (m.matches())
      {
        return;
      }
    }
    CssLocation location = e.getLocation();
    report.message(MessageId.CSS_008, getCorrectedMessageLocation(path, location.getLine(), location.getColumn(), null), e.getMessage());
  }

  @Override
  public void startDocument()
  {
  }

  @Override
  public void endDocument()
  {
  }

  static final Pattern keyframesPattern = Pattern.compile("@((keyframes)|(-moz-keyframes)|(-webkit-keyframes)|(-o-keyframes))");

  @Override
  public void startAtRule(CssAtRule atRule)
  {
    String ruleName = atRule.getName().get();
    this.atRule = atRule;
    if (ruleName.equals("@import"))
    {
      CssConstruct uriOrString = atRule.getComponents().get(0);
      if (uriOrString != null)
      {
        int line = uriOrString.getLocation().getLine();
        int col = uriOrString.getLocation().getColumn();

        if (uriOrString.getType() == CssConstruct.Type.URI)
        {
          resolveAndRegister(((CssURI) uriOrString).toUriString(), line, col, atRule.toCssString());
        }
        else if (uriOrString.getType() == CssConstruct.Type.STRING)
        {
          String uri = CharMatcher.anyOf("\"'").trimFrom(uriOrString.toCssString());
          resolveAndRegister(uri, line, col, atRule.toCssString());
        }
        else
        {
          //syntax error, url must be first parameter
        }
      }
    }
    else if(atRule.getName().get().equals("@namespace"))
    {
	    //do not register namespace URIs as resources...
    }
    else
    {
      //check generically for urls in other atrules
      registerURIs(atRule.getComponents(),
          atRule.getLocation().getLine(),
          atRule.getLocation().getColumn());
    }

    if (ruleName.equals("@font-face"))
    {
      inFontFace = true;
    }
    else if (keyframesPattern.matcher(ruleName).matches())
    {
      inKeyFrames=true;
    }
  }

  @Override
  public void endAtRule(String name)
  {
    if (inFontFace)
    {
      inFontFace = false;
      handleFontFaceInfo();
      if (!hasFontFaceDeclarations)
      {
        report.message(MessageId.CSS_019, new MessageLocation(path, atRule.getLocation().getLine(), atRule.getLocation().getColumn(), atRule.toCssString()));
      }
      hasFontFaceDeclarations = false;
    }
    if (inKeyFrames)
    {
      inKeyFrames = false;
    }
    atRule = null;
  }

  @Override
  public void selectors(List<CssSelector> selectors)
  {
  }

  @Override
  public void endSelectors(List<CssSelector> selectors)
  {
  }

  @Override
  public void declaration(CssDeclaration declaration)
  {
    registerURIs(declaration.getComponents(),
        declaration.getLocation().getLine(),
        declaration.getLocation().getColumn());

    String propertyName = declaration.getName().get();
    if (propertyName == null)
    {
      return;
    }

    if (version == EPUBVersion.VERSION_3)
    {
      if (propertyName.equals("position"))
      {
        CssConstruct cns = declaration.getComponents().get(0);
        if (cns != null)
        {
          String value = cns.toCssString();
          if (value != null && value.equalsIgnoreCase("fixed"))
          {
            report.message(MessageId.CSS_006, getCorrectedMessageLocation(path, declaration.getLocation().getLine(), declaration.getLocation().getColumn(), declaration.toCssString()));
          }
        }
      }
      else if (propertyName.equals("direction") || propertyName.equals("unicode-bidi"))
      {
        report.message(MessageId.CSS_001, getCorrectedMessageLocation(path, declaration.getLocation().getLine(), declaration.getLocation().getColumn(), declaration.toCssString()), propertyName);
      }
    }

    if (inFontFace)
    {
      hasFontFaceDeclarations = true;

      //collect for info
      if (propertyName.equals("font-family"))
      {
        CssConstruct cc = declaration.getComponents().get(0);
        if (cc != null)
        {
          fontFamily = SPACE_AND_QUOTES.trimFrom(cc.toCssString());
        }
      }
      else if (propertyName.equals("font-style"))
      {
        CssConstruct cc = declaration.getComponents().get(0);
        fontStyle = cc.toCssString();
      }
      else if (propertyName.equals("font-weight"))
      {
        CssConstruct cc = declaration.getComponents().get(0);
        fontWeight = cc.toCssString();
      }
      else if (propertyName.equals("src"))
      {
        for (CssConstruct construct : declaration.getComponents())
        {
          if (construct.getType() == CssConstruct.Type.URI)
          {
            fontUri = ((CssURI) construct).toUriString();
            fontUri = PathUtil.resolveRelativeReference(path, fontUri, null);
            //check font mimetypes
            String fontMimeType = xrefChecker.getMimeType(fontUri);
            if (fontMimeType != null)
            {
              boolean blessed = true;
              if (version == EPUBVersion.VERSION_2)
              {
                blessed = OPFChecker.isBlessedFontMimetype20(fontMimeType);
              }
              else if (version == EPUBVersion.VERSION_3)
              {
                blessed = OPFChecker30.isBlessedFontType(fontMimeType);
              }
              if (!blessed)
              {
                report.message(MessageId.CSS_007,
                    getCorrectedMessageLocation(path, declaration.getLocation().getLine(), declaration.getLocation().getColumn(), declaration.toCssString()),
                    fontUri,
                    fontMimeType);
              }
            }
            else
            {
              //errors sb reported elsewhere
            }
          }
        }
      }
    }
  }

  private void registerURIs(List<CssConstruct> constructs, int line, int col)
  {
    for (CssConstruct construct : constructs)
    {
      if (construct.getType() == CssConstruct.Type.URI)
      {
        resolveAndRegister(((CssURI) construct).toUriString(), line, col, construct.toCssString());
      }
    }
  }

  private void resolveAndRegister(String relativeRef, int line, int col, String context)
  {
    if (relativeRef != null && relativeRef.trim().length() > 0)
    {
      String resolved = PathUtil.resolveRelativeReference(path, relativeRef, null);
      xrefChecker.registerReference(path, line + startingLineNumber, col, resolved, XRefChecker.RT_GENERIC);
    }
    else
    {
      report.message(MessageId.CSS_002,getCorrectedMessageLocation(path, line, col, context));
    }
  }

  private void handleFontFaceInfo()
  {
    if (fontFamily != null)
    {
      if (fontUri != null && !fontUri.startsWith("http"))
      {
        report.info(path, FeatureEnum.FONT_EMBEDDED, fontFamily +
            (((fontStyle != null) && !"normal".equalsIgnoreCase(fontStyle)) ? "," + fontStyle : "") +
            (((fontWeight != null) && !"normal".equalsIgnoreCase(fontWeight)) ? "," + fontWeight : "")
        );
      }
      else
      {
        report.info(path, FeatureEnum.FONT_REFERENCE, fontFamily +
            (((fontStyle != null) && !"normal".equalsIgnoreCase(fontStyle)) ? "," + fontStyle : "") +
            (((fontWeight != null) && !"normal".equalsIgnoreCase(fontWeight)) ? "," + fontWeight : "")
        );
        report.info(path, FeatureEnum.REFERENCE, fontUri);
      }
    }
  }

  public void setStartingLineNumber(int offset)
  {
    this.startingLineNumber = offset - 1;
    if (this.startingLineNumber < 0)
    {
      this.startingLineNumber = 0;
    }
  }
}
