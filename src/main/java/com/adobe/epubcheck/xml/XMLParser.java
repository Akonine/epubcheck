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
 */

package com.adobe.epubcheck.xml;

import com.adobe.epubcheck.api.Report;
import com.adobe.epubcheck.messages.MessageId;
import com.adobe.epubcheck.messages.MessageLocation;
import com.adobe.epubcheck.ocf.OCFPackage;
import com.adobe.epubcheck.util.EPUBVersion;
import com.adobe.epubcheck.util.ResourceUtil;
import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.Validator;
import org.xml.sax.*;
import org.xml.sax.ext.DeclHandler;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.ext.Locator2;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.*;

public class XMLParser extends DefaultHandler implements LexicalHandler, DeclHandler
{
  private static final String SAXPROP_LEXICAL_HANDLER = "http://xml.org/sax/properties/lexical-handler";
  private static final String SAXPROP_DECL_HANDLER = "http://xml.org/sax/properties/declaration-handler";
  private SAXParser parser;
  private final Report report;
  private final String resource;
  private final InputStream resourceIn;
  private final Vector<XMLHandler> contentHandlers = new Vector<XMLHandler>();
  private XMLElement currentElement;
  private final Vector<ContentHandler> validatorContentHandlers = new Vector<ContentHandler>();
  private final Vector<DTDHandler> validatorDTDHandlers = new Vector<DTDHandler>();
  private final Vector<LexicalHandler> validatorLexicalHandlers = new Vector<LexicalHandler>();
  private final Vector<DeclHandler> validatorDeclHandlers = new Vector<DeclHandler>();
  private Locator2 documentLocator;
  private final EPUBVersion version;
  private static final String zipRoot = "file:///epub-root/";
  private static final Hashtable<String, String> systemIdMap;
  private final HashSet<String> entities = new HashSet<String>();
  private final String mimeType;
  private boolean firstStartDTDInvocation = true;
  private OCFPackage thePackage;

  public XMLParser(OCFPackage thePackage, InputStream resourceIn, String entryName, String mimeType,
      Report report, EPUBVersion version)
  {
    this.report = report;
    this.resource = entryName;
    this.resourceIn = resourceIn;
    this.mimeType = mimeType;
    this.version = version;
    this.thePackage = thePackage;

    // XML predefined
    entities.add("gt");
    entities.add("lt");
    entities.add("amp");
    entities.add("quot");
    entities.add("apos");

    SAXParserFactory factory = SAXParserFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setValidating(false);

    try
    {
      factory.setFeature("http://xml.org/sax/features/validation", false);
      if (version == EPUBVersion.VERSION_3)
      {
        factory.setXIncludeAware(false);
      }
    }
    catch (Exception ignored)
    {
    }

    try
    {
      parser = factory.newSAXParser();

      XMLReader reader = parser.getXMLReader();
      reader.setDTDHandler(this);
      reader.setContentHandler(this);
      reader.setEntityResolver(this);
      reader.setErrorHandler(this);

      try
      {
        reader.setProperty(SAXPROP_LEXICAL_HANDLER, this);
        reader.setProperty(SAXPROP_DECL_HANDLER, this);
      }
      catch (SAXNotRecognizedException e)
      {
        e.printStackTrace();
      }
      catch (SAXNotSupportedException e)
      {
        e.printStackTrace();
      }
    }
    catch (ParserConfigurationException e)
    {
      e.printStackTrace();
    }
    catch (SAXException e)
    {
      e.printStackTrace();
    }
  }


  public void addXMLHandler(XMLHandler handler)
  {
    if (handler != null)
    {
      contentHandlers.add(handler);
    }
  }

  public void addValidator(XMLValidator xv)
  {
    PropertyMapBuilder propertyMapBuilder = new PropertyMapBuilder();
    propertyMapBuilder.put(ValidateProperty.ERROR_HANDLER, this);
    Validator validator = xv.schema.createValidator(propertyMapBuilder
        .toPropertyMap());
    ContentHandler contentHandler = validator.getContentHandler();
    if (contentHandler != null)
    {
      validatorContentHandlers.add(contentHandler);
    }
    DTDHandler dtdHandler = validator.getDTDHandler();
    if (dtdHandler != null)
    {
      validatorDTDHandlers.add(dtdHandler);
    }
  }

  public void addDeclHandler(DeclHandler handler)
  {
    if (handler != null)
    {
      validatorDeclHandlers.add(handler);
    }
  }

  public void addLexicalHandler(LexicalHandler handler)
  {
    if (handler != null)
    {
      validatorLexicalHandlers.add(handler);
    }
  }


  public void process()
  {
    InputStream in = resourceIn;
    try
    {
      //System.err.println("DEBUG XMLParser#process on" + resource);
      if (!in.markSupported())
      {
        in = new BufferedInputStream(in);
      }

      String encoding = sniffEncoding(in);
      if (encoding != null && !encoding.equals("UTF-8")
          && !encoding.equals("UTF-16"))
      {
        report.message(MessageId.CSS_003, new MessageLocation(resource, 0, 0, ""), encoding);
      }

      InputSource ins = new InputSource(in);
      ins.setSystemId(zipRoot + resource);
      parser.parse(ins, this);

    }
    catch (FileNotFoundException e)
    {
      String message = e.getMessage();
      message = new File(message).getName();
      int p = message.indexOf("(");
      if (p > 0)
      {
        message = message.substring(0, message.indexOf("("));
      }
      message = message.trim();
      report.message(MessageId.RSC_001, new MessageLocation(resource, -1, -1), message);
    }
    catch (IOException e)
    {
      report.message(MessageId.PKG_008, new MessageLocation(resource, 0, 0), resource);
    }
    catch (IllegalArgumentException e)
    {
      report.message(MessageId.RSC_005, new MessageLocation(resource, 0, 0), e.getMessage());
    }
    catch (SAXException e)
    {
      report.message(MessageId.RSC_005, new MessageLocation(resource, 0, 0), e.getMessage());
    }
    catch (NullPointerException e)
    {
      // this happens for unresolved entities, reported in entityResolver
      // code.
    }
    finally
    {
      try
      {
        in.close();
      }
      catch (IOException ignored)
      {
      }
    }
  }

  public InputSource resolveEntity(String publicId, String systemId)
      throws
      SAXException,
      IOException
  {
    //if (systemId.startsWith(zipRoot))
    //{
    //  InputStream inStream = this.thePackage.getInputStream(systemId.substring(zipRoot.length()));
    //  if (inStream != null)
    //  {
    //    InputSource source = new InputSource(inStream);
    //    source.setPublicId(publicId);
    //    source.setSystemId(systemId);
    //    return source;
    //  }
    //}
    //outWriter.println("DEBUG XMLParser#resolveEntity ==> "+ publicId + ", " + systemId + ", " );

    String resourcePath = systemIdMap.get(systemId);

    if (resourcePath != null)
    {
      InputStream resourceStream = ResourceUtil.getResourceStream(resourcePath);
      InputSource source = new InputSource(resourceStream);
      source.setPublicId(publicId);
      source.setSystemId(systemId);
      return source;
    }
    else if (systemId.equals("about:legacy-compat"))
    {
      //special case
      return new InputSource(new StringReader(""));

    }
    else
    {
      //check for a system prop that turns off online fetching
      //the default is to attempt online fetching, as this has been the default forever
      boolean offline = Boolean.parseBoolean(System.getProperty("epubcheck.offline"));
      //outWriter.println("offline value is " + offline);
      if (systemId.startsWith("http:") && offline)
      {
        return new InputSource(new StringReader(""));
      }
      //else return null and let the caller try to fetch the goods
      return null;
    }
  }


  public void notationDecl(String name, String publicId, String systemId)
      throws
      SAXException
  {
    int len = validatorDTDHandlers.size();
    for (int i = 0; i < len; i++)
    {
      (validatorDTDHandlers.elementAt(i)).notationDecl(name,
          publicId, systemId);
    }
  }

  public void unparsedEntityDecl(String name, String publicId,
      String systemId, String notationName) throws
      SAXException
  {
    int len = validatorDTDHandlers.size();
    for (int i = 0; i < len; i++)
    {
      (validatorDTDHandlers.elementAt(i))
          .unparsedEntityDecl(name, publicId, systemId, notationName);
    }
  }

  public void error(SAXParseException ex) throws
      SAXException
  {
    report.message(MessageId.RSC_005,
        new MessageLocation(resource, ex.getLineNumber(), ex.getColumnNumber()),
        ex.getMessage());
  }

  public void fatalError(SAXParseException ex) throws
      SAXException
  {
    report.message(MessageId.RSC_016,
        new MessageLocation(resource, ex.getLineNumber(), ex.getColumnNumber()),
        ex.getMessage());
  }

  public void warning(SAXParseException ex) throws
      SAXException
  {
    report.message(MessageId.RSC_017,
        new MessageLocation(resource, ex.getLineNumber(), ex.getColumnNumber()),
        ex.getMessage());
  }

  public void characters(char[] arg0, int arg1, int arg2) throws
      SAXException
  {
    int vlen = validatorContentHandlers.size();
    for (int i = 0; i < vlen; i++)
    {
      (validatorContentHandlers.elementAt(i))
          .characters(arg0, arg1, arg2);
    }

    int len = contentHandlers.size();
    for (int i = 0; i < len; i++)
    {
      (contentHandlers.elementAt(i)).characters(arg0, arg1,
          arg2);
    }
  }

  public void endDocument() throws
      SAXException
  {
    int len = validatorContentHandlers.size();
    for (int i = 0; i < len; i++)
    {
      (validatorContentHandlers.elementAt(i))
          .endDocument();
    }
  }

  public void endElement(String arg0, String arg1, String arg2)
      throws
      SAXException
  {
    int vlen = validatorContentHandlers.size();
    for (int i = 0; i < vlen; i++)
    {
      (validatorContentHandlers.elementAt(i))
          .endElement(arg0, arg1, arg2);
    }
    int len = contentHandlers.size();
    for (int i = 0; i < len; i++)
    {
      (contentHandlers.elementAt(i)).endElement();
    }
    currentElement = currentElement.getParent();
  }

  public void endPrefixMapping(String arg0) throws
      SAXException
  {
    int vlen = validatorContentHandlers.size();
    for (int i = 0; i < vlen; i++)
    {
      (validatorContentHandlers.elementAt(i))
          .endPrefixMapping(arg0);
    }
  }

  public void ignorableWhitespace(char[] arg0, int arg1, int arg2)
      throws
      SAXException
  {
    int vlen = validatorContentHandlers.size();
    for (int i = 0; i < vlen; i++)
    {
      (validatorContentHandlers.elementAt(i))
          .ignorableWhitespace(arg0, arg1, arg2);
    }
    int len = contentHandlers.size();
    for (int i = 0; i < len; i++)
    {
      (contentHandlers.elementAt(i)).ignorableWhitespace(
          arg0, arg1, arg2);
    }
  }

  public void processingInstruction(String arg0, String arg1)
      throws
      SAXException
  {
    int vlen = validatorContentHandlers.size();
    for (int i = 0; i < vlen; i++)
    {
      (validatorContentHandlers.elementAt(i))
          .processingInstruction(arg0, arg1);
    }
    int len = contentHandlers.size();
    for (int i = 0; i < len; i++)
    {
      (contentHandlers.elementAt(i)).processingInstruction(
          arg0, arg1);
    }
  }

  public void setDocumentLocator(Locator locator)
  {
    int vlen = validatorContentHandlers.size();
    for (int i = 0; i < vlen; i++)
    {
      (validatorContentHandlers.elementAt(i))
          .setDocumentLocator(locator);
    }
    documentLocator = new DocumentLocatorImpl(locator);
  }

  public void skippedEntity(String arg0) throws
      SAXException
  {
    int vlen = validatorContentHandlers.size();
    for (int i = 0; i < vlen; i++)
    {
      (validatorContentHandlers.elementAt(i))
          .skippedEntity(arg0);
    }
  }

  public void startDocument() throws
      SAXException
  {
    int vlen = validatorContentHandlers.size();
    for (int i = 0; i < vlen; i++)
    {
      (validatorContentHandlers.elementAt(i))
          .startDocument();
    }
  }

  public void startElement(String namespaceURI, String localName,
      String qName, Attributes atts) throws
      SAXException
  {

    AttributesImpl attribs = new AttributesImpl(atts);

    if (mimeType.equals("application/xhtml+xml")
        && version == EPUBVersion.VERSION_3)
    {
      try
      {
        int len = attribs.getLength();
        List<String> removals = new ArrayList<String>();
        for (int i = 0; i < len; i++)
        {
          if (attribs.getLocalName(i).startsWith("data-"))
          {
            removals.add(attribs.getQName(i));
          }
        }
        for (String remove : removals)
        {
          int rmv = attribs.getIndex(remove);
          // outWriter.println("removing attribute " +
          // attribs.getQName(rmv));
          attribs.removeAttribute(rmv);
        }
      }
      catch (Exception e)
      {
        System.err.println("data-* removal exception: "
            + e.getMessage());
      }
    }

    int vlen = validatorContentHandlers.size();
    for (int i = 0; i < vlen; i++)
    {
      (validatorContentHandlers.elementAt(i))
          .startElement(namespaceURI, localName, qName, attribs);
    }
    int index = qName.indexOf(':');
    String prefix;
    String name;
    if (index < 0)
    {
      prefix = null;
      name = qName;
    }
    else
    {
      prefix = qName.substring(0, index);
      name = qName.substring(index + 1);
    }
    int count = attribs.getLength();
    XMLAttribute[] attributes = count == 0 ? null : new XMLAttribute[count];
    for (int i = 0; i < count; i++)
    {
      String attName = attribs.getLocalName(i);
      String attNamespace = attribs.getURI(i);
      String attQName = attribs.getQName(i);
      int attIndex = attQName.indexOf(':');
      String attPrefix;
      if (attIndex < 0)
      {
        attPrefix = null;
        attNamespace = null;
      }
      else
      {
        attPrefix = attQName.substring(0, attIndex);
      }
      String attValue = attribs.getValue(i);
      assert attributes != null;
      attributes[i] = new XMLAttribute(attNamespace, attPrefix, attName,
          attValue);
    }
    currentElement = new XMLElement(namespaceURI, prefix, name, attributes,
        currentElement);
    int len = contentHandlers.size();
    for (int i = 0; i < len; i++)
    {
      (contentHandlers.elementAt(i)).startElement();
    }
  }

  public void startPrefixMapping(String arg0, String arg1)
      throws
      SAXException
  {
    int vlen = validatorContentHandlers.size();
    for (int i = 0; i < vlen; i++)
    {
      (validatorContentHandlers.elementAt(i))
          .startPrefixMapping(arg0, arg1);
    }
  }

  public void comment(char[] text, int arg1, int arg2) throws
      SAXException
  {
    if (validatorLexicalHandlers.size() > 0)
    {
      for (LexicalHandler h : this.validatorLexicalHandlers)
      {
        h.comment(text, arg1, arg2);
      }
    }
  }

  public void endCDATA() throws
      SAXException
  {
    if (validatorLexicalHandlers.size() > 0)
    {
      for (LexicalHandler h : this.validatorLexicalHandlers)
      {
        h.endCDATA();
      }
    }
  }

  public void endDTD() throws
      SAXException
  {
    if (validatorLexicalHandlers.size() > 0)
    {
      for (LexicalHandler h : this.validatorLexicalHandlers)
      {
        h.endDTD();
      }
    }
  }

  public void endEntity(String ent) throws
      SAXException
  {
    if (validatorLexicalHandlers.size() > 0)
    {
      for (LexicalHandler h : this.validatorLexicalHandlers)
      {
        h.endEntity(ent);
      }
    }
  }

  public void startCDATA() throws
      SAXException
  {
    if (validatorLexicalHandlers.size() > 0)
    {
      for (LexicalHandler h : this.validatorLexicalHandlers)
      {
        h.startCDATA();
      }
    }
  }

  public void startDTD(String root, String publicId, String systemId)
      throws
      SAXException
  {
    if (validatorLexicalHandlers.size() > 0)
    {
      for (LexicalHandler h : this.validatorLexicalHandlers)
      {
        h.startDTD(root, publicId, systemId);
      }
    }

    handleDocTypeUserInfo(root, publicId, systemId);
  }

  private void handleDocTypeUserInfo(String root, String publicId, String systemId)
  {
    //outWriter.println("DEBUG doctype ==> "+ root + ", " + publicId + ", " + systemId + ", " );

    //for modular DTDs etc, just issue a warning for the top level IDs.
    if (!firstStartDTDInvocation)
    {
      return;
    }

    if (version == EPUBVersion.VERSION_2)
    {

      if (mimeType != null && "application/xhtml+xml".equals(mimeType) && root.equals("html"))
      {
        //OPS 2.0(.1)
        String complete = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \n" +
            "\"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">";

        if (matchDoctypeId("-//W3C//DTD XHTML 1.1//EN", publicId, complete))
        {
          matchDoctypeId("http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd", systemId, complete);
        }

      }

      if (mimeType != null && "opf".equals(mimeType) && (publicId != null || systemId != null))
      {

        //1.2: <!DOCTYPE package PUBLIC "+//ISBN 0-9673008-1-9//DTD OEB 1.2 Package//EN" "http://openebook.org/dtds/oeb-1.2/oebpkg12.dtd">
        //http://http://idpf.org/dtds/oeb-1.2/oebpkg12.dtd
        if ("package".equals(root)
            && (publicId == null || publicId.equals("+//ISBN 0-9673008-1-9//DTD OEB 1.2 Package//EN"))
            && (systemId == null || systemId.equals("http://openebook.org/dtds/oeb-1.2/oebpkg12.dtd"))
            )
        {
          //for heritage content collections, dont warn about this, as its not explicitly forbidden by the spec
        }
        else
        {
          report.message(MessageId.HTM_009, new MessageLocation(resource, 0, 0));
        }

      }

      if (mimeType != null && "application/x-dtbncx+xml".equals(mimeType))
      {
        String complete = "<!DOCTYPE ncx PUBLIC \"-//NISO//DTD ncx 2005-1//EN\" " +
            "\n \"http://www.daisy.org/z3986/2005/ncx-2005-1.dtd\">";
        if (matchDoctypeId("-//NISO//DTD ncx 2005-1//EN", publicId, complete))
        {
          matchDoctypeId("http://www.daisy.org/z3986/2005/ncx-2005-1.dtd", systemId, complete);
        }
      }

    }
    else if (version == EPUBVersion.VERSION_3)
    {
      if (mimeType != null && "application/xhtml+xml".equals(mimeType) && "html".equalsIgnoreCase(root))
      {
        String complete = "<!DOCTYPE html>";
        //warn for obsolete or unknown doctypes
        if (publicId == null && (systemId == null || systemId.equals("about:legacy-compat")))
        {
          // we assume to have have <!DOCTYPE html> or <!DOCTYPE html SYSTEM "about:legacy-compat">
        }
        else
        {
          report.message(MessageId.HTM_004, new MessageLocation(resource, 0, 0), publicId, complete);
        }
      }
      else if ("image/svg+xml".equals(mimeType) && "svg".equalsIgnoreCase(root))
      {
        if (
            !(checkDTD("-//W3C//DTD SVG 1.1//EN", "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd", publicId, systemId)  ||
              checkDTD("-//W3C//DTD SVG 1.0//EN", "http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd", publicId, systemId)  ||
              checkDTD("-//W3C//DTD SVG 1.1 Basic//EN", "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11-basic.dtd", publicId, systemId)  ||
              checkDTD("-//W3C//DTD SVG 1.1 Tiny//EN", "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11-tiny.dtd", publicId, systemId))
           )
        {
          report.message(MessageId.HTM_009, new MessageLocation(resource, 0, 0));
        }
      }
      else if (mimeType != null && "application/x-dtbncx+xml".equals(mimeType))
      {
        String complete = "<!DOCTYPE ncx PUBLIC \"-//NISO//DTD ncx 2005-1//EN\" " +
            "\n \"http://www.daisy.org/z3986/2005/ncx-2005-1.dtd\">";
        if (matchDoctypeId("-//NISO//DTD ncx 2005-1//EN", publicId, complete))
        {
          matchDoctypeId("http://www.daisy.org/z3986/2005/ncx-2005-1.dtd", systemId, complete);
        }
      }
      else
      {
        report.message(MessageId.HTM_009, new MessageLocation(resource, 0, 0));
      }
    }

    firstStartDTDInvocation = false;
  }

  boolean checkDTD(String expectedPublicId, String expectedSystemId, String actualPublicId, String actualSystemId)
  {
    if ((actualPublicId == null || (actualPublicId != null && expectedPublicId.equalsIgnoreCase(actualPublicId))) &&
        (actualSystemId == null || (actualSystemId != null && expectedSystemId.equalsIgnoreCase(actualSystemId))))
    {
      return true;
    }
    return false;
  }

  boolean matchDoctypeId(String expected, String given, String messageParam)
  {
    if (given != null && !expected.equals(given))
    {
      report.message(MessageId.HTM_004, new MessageLocation(resource, 0, 0), given, messageParam);
      return false;
    }
    return true;
  }

  public void startEntity(String ent) throws
      SAXException
  {
    if (validatorLexicalHandlers.size() > 0)
    {
      for (LexicalHandler h : this.validatorLexicalHandlers)
      {
        h.startEntity(ent);
      }
    }
    if (!entities.contains(ent) && !ent.equals("[dtd]"))
    {
      // This message may never be reported.  Undeclared entities result in a Sax Parser Error and message RSC_005.
      report.message(MessageId.HTM_011, new MessageLocation(resource, getLineNumber(), getColumnNumber(), ent));
    }
  }

  public void attributeDecl(String name, String name2, String type,
      String mode, String value) throws
      SAXException
  {
    if (validatorDeclHandlers.size() > 0)
    {
      for (DeclHandler h : this.validatorDeclHandlers)
      {
        h.attributeDecl(name, name2, type, mode, value);
      }
    }
  }

  public void elementDecl(String name, String model) throws
      SAXException
  {
    if (validatorDeclHandlers.size() > 0)
    {
      for (DeclHandler h : this.validatorDeclHandlers)
      {
        h.elementDecl(name, model);
      }
    }
  }

  public void externalEntityDecl(String name, String publicId, String systemId)
      throws
      SAXException
  {
    if (validatorDeclHandlers.size() > 0)
    {
      for (DeclHandler h : this.validatorDeclHandlers)
      {
        h.externalEntityDecl(name, publicId, systemId);
      }
    }

    if (version == EPUBVersion.VERSION_3 && (mimeType.compareTo("application/xhtml+xml") == 0))
    {
      report.message(MessageId.HTM_003, new MessageLocation(resource, getLineNumber(), getColumnNumber(), name), name);
      return;
    }
    entities.add(name);
  }

  public void internalEntityDecl(String name, String value)
      throws
      SAXException
  {
    if (validatorDeclHandlers.size() > 0)
    {
      for (DeclHandler h : this.validatorDeclHandlers)
      {
        h.internalEntityDecl(name, value);
      }
    }
    entities.add(name);
  }

  public XMLElement getCurrentElement()
  {
    return currentElement;
  }

  public Report getReport()
  {
    return report;
  }

  public int getLineNumber()
  {
    return documentLocator.getLineNumber();
  }

  public int getColumnNumber()
  {
    return documentLocator.getColumnNumber();
  }

  public String getXMLVersion()
  {
    return documentLocator.getXMLVersion();
  }

  public String getResourceName()
  {
    return resource;
  }

  private static final byte[][] utf16magic = {{(byte) 0xFE, (byte) 0xFF},
      {(byte) 0xFF, (byte) 0xFE}, {0, 0x3C, 0, 0x3F},
      {0x3C, 0, 0x3F, 0}};

  private static final byte[][] ucs4magic = {{0, 0, (byte) 0xFE, (byte) 0xFF},
      {(byte) 0xFF, (byte) 0xFE, 0, 0},
      {0, 0, (byte) 0xFF, (byte) 0xFE},
      {(byte) 0xFE, (byte) 0xFF, 0, 0}, {0, 0, 0, 0x3C},
      {0, 0, 0x3C, 0}, {0, 0x3C, 0, 0}, {0x3C, 0, 0, 0}};

  private static final byte[] utf8magic = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  private static final byte[] ebcdicmagic = {0x4C, 0x6F, (byte) 0xA7, (byte) 0x94};

  private static boolean matchesMagic(byte[] magic, byte[] buffer)
  {
    for (int i = 0; i < magic.length; i++)
    {
      if (buffer[i] != magic[i])
      {
        return false;
      }
    }
    return true;
  }

  private static String sniffEncoding(InputStream in) throws
      IOException
  {
    // see http://www.w3.org/TR/REC-xml/#sec-guessing
    byte[] buffer = new byte[256];
    in.mark(buffer.length);
    int len = in.read(buffer);
    in.reset();
    if (len < 4)
    {
      return null;
    }
    for (byte[] magic : utf16magic)
    {
      if (matchesMagic(magic, buffer))
      {
        return "UTF-16";
      }
    }
    for (byte[] anUcs4magic : ucs4magic)
    {
      if (matchesMagic(anUcs4magic, buffer))
      {
        return "UCS-4";
      }
    }
    if (matchesMagic(utf8magic, buffer))
    {
      return "UTF-8";
    }
    if (matchesMagic(ebcdicmagic, buffer))
    {
      return "EBCDIC";
    }

    // some ASCII-compatible encoding; read ASCII
    int asciiLen = 0;
    while (asciiLen < len)
    {
      int c = buffer[asciiLen] & 0xFF;
      if (c == 0 || c > 0x7F)
      {
        break;
      }
      asciiLen++;
    }

    // read it into a String
    String header = new String(buffer, 0, asciiLen, "ASCII");
    int encIndex = header.indexOf("encoding=");
    if (encIndex < 0)
    {
      return null; // probably UTF-8
    }

    encIndex += 9;
    if (encIndex >= header.length())
    {
      return null; // encoding did not fit!
    }

    char quote = header.charAt(encIndex);
    if (quote != '"' && quote != '\'')
    {
      return null; // confused...
    }

    int encEnd = header.indexOf(quote, encIndex + 1);
    if (encEnd < 0)
    {
      return null; // encoding did not fit!
    }

    String encoding = header.substring(encIndex + 1, encEnd);
    return encoding.toUpperCase();
  }

  static
  {
    Hashtable<String, String> map = new Hashtable<String, String>();

    // OEB 1.2
    map.put("http://openebook.org/dtds/oeb-1.2/oebpkg12.dtd",
        ResourceUtil.getResourcePath("schema/20/dtd/oebpkg12.dtd"));
    map.put("http://http://idpf.org/dtds/oeb-1.2/oebpkg12.dtd",
        ResourceUtil.getResourcePath("schema/20/dtd/oebpkg12.dtd"));
    map.put("http://openebook.org/dtds/oeb-1.2/oeb12.ent",
        ResourceUtil.getResourcePath("schema/20/dtd/oeb12.dtdinc"));

    //2.0 dtd, probably never published
    map.put("http://www.idpf.org/dtds/2007/opf.dtd",
        ResourceUtil.getResourcePath("schema/20/dtd/opf20.dtd"));
    //xhtml 1.1
    map.put("http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd",
        ResourceUtil.getResourcePath("schema/20/dtd/xhtml1-transitional.dtd"));
    map.put("http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd",
        ResourceUtil.getResourcePath("schema/20/dtd/xhtml1-strict.dtd"));
    map.put("http://www.w3.org/TR/xhtml1/DTD/xhtml-lat1.ent",
        ResourceUtil.getResourcePath("schema/20/dtd/xhtml-lat1.dtdinc"));
    map.put("http://www.w3.org/TR/xhtml1/DTD/xhtml-symbol.ent",
        ResourceUtil.getResourcePath("schema/20/dtd/xhtml-symbol.dtdinc"));
    map.put("http://www.w3.org/TR/xhtml1/DTD/xhtml-special.ent",
        ResourceUtil.getResourcePath("schema/20/dtd/xhtml-special.dtdinc"));
    //svg 1.1
    map.put("http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd",
        ResourceUtil.getResourcePath("schema/20/dtd/svg11.dtd"));
    //dtbook
    map.put("http://www.daisy.org/z3986/2005/dtbook-2005-2.dtd",
        ResourceUtil.getResourcePath("schema/20/dtd/dtbook-2005-2.dtd"));
    //ncx
    map.put("http://www.daisy.org/z3986/2005/ncx-2005-1.dtd",
        ResourceUtil.getResourcePath("schema/20/dtd/ncx-2005-1.dtd"));

    //xhtml 1.1: just reference the character entities, as we validate with rng
    map.put("http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd",
        ResourceUtil.getResourcePath("schema/20/dtd/xhtml11-ent.dtd"));
    map.put("http://www.w3.org/MarkUp/DTD/xhtml11.dtd",
        ResourceUtil.getResourcePath("schema/20/dtd/xhtml11-ent.dtd"));

    // non-resolved names; Saxon (which schematron requires and registers as
    // preferred parser, it seems) passes us those (bad, bad!), work around it
    map.put("xhtml-lat1.ent",
        ResourceUtil.getResourcePath("dtd/xhtml-lat1.dtdinc"));
    map.put("xhtml-symbol.ent",
        ResourceUtil.getResourcePath("dtd/xhtml-symbol.dtdinc"));
    map.put("xhtml-special.ent",
        ResourceUtil.getResourcePath("dtd/xhtml-special.dtdinc"));
    systemIdMap = map;
  }
}
