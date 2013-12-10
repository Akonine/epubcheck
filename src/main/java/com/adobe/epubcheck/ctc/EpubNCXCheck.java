package com.adobe.epubcheck.ctc;

import com.adobe.epubcheck.api.Report;
import com.adobe.epubcheck.ctc.epubpackage.*;
import com.adobe.epubcheck.messages.MessageId;
import com.adobe.epubcheck.messages.MessageLocation;
import com.adobe.epubcheck.opf.DocumentValidator;
import com.adobe.epubcheck.reporting.CheckingReport;
import com.adobe.epubcheck.util.EPUBVersion;
import com.adobe.epubcheck.util.FeatureEnum;
import com.adobe.epubcheck.util.PathUtil;
import org.w3c.dom.*;

import java.util.HashSet;

public class EpubNCXCheck implements DocumentValidator
{
  private final XmlDocParser docParser;
  private final Document doc;
  private final String pathRootFile;
  private final Report report;
  private final EpubPackage epack;
  private String ncxDoc;


  public EpubNCXCheck(EpubPackage epack, Report report)
  {
    this.doc = epack.getPackDoc();
    this.report = report;
    this.pathRootFile = epack.getPackageMainFile();
    this.epack = epack;
    docParser = new XmlDocParser(epack.getZip(), report);
  }

  @Override
  public boolean validate()
  {
    boolean result = isNCXDefined(doc);
    if (result && epack.getVersion() == EPUBVersion.VERSION_2)
    {
      String fileToParse;
      if (epack.getPackageMainPath() != null && epack.getPackageMainPath().length() > 0)
      {
        fileToParse = PathUtil.resolveRelativeReference(epack.getPackageMainFile(), ncxDoc, null);
      }
      else
      {
        fileToParse = ncxDoc;
      }
      checkNcxDoc(fileToParse);
    }

    if (!result && epack.getVersion() != EPUBVersion.VERSION_2)
    {
      if (report.getClass() == CheckingReport.class)
      {
        report.message(MessageId.NCX_003, new MessageLocation(pathRootFile, -1, -1));
      }
      else
      {
        report.info(pathRootFile, FeatureEnum.HAS_NCX, "false");
      }
    }

    return result;
  }

  private boolean isNCXDefined(Document doc)
  {
    boolean isNCXdefined = false;
    NodeList spineList = doc.getElementsByTagName("spine");
    if (spineList.getLength() > 0)
    {
      for (int i = 0; i < spineList.getLength(); i++)
      {
        NamedNodeMap attrs = spineList.item(i).getAttributes();
        Node n = attrs.getNamedItem("toc");
        if (n != null)
        {
          String tocID = n.getNodeValue();
          NodeList manifestList = doc.getElementsByTagName("manifest");
          for (int m = 0; m < manifestList.getLength(); m++)
          {
            Node manifestNode = manifestList.item(m);
            NodeList itemNodes = manifestNode.getChildNodes();

            for (int it = 0; it < itemNodes.getLength(); it++)
            {
              NamedNodeMap itemNodeAttributes = itemNodes.item(it).getAttributes();
              if (itemNodeAttributes != null)
              {
                String manifestNodeID = itemNodeAttributes.getNamedItem("id").getNodeValue();
                if (manifestNodeID != null && manifestNodeID.compareToIgnoreCase(tocID) == 0 && itemNodeAttributes.getNamedItem("href").getNodeValue() != null)
                {
                  isNCXdefined = true;
                  this.ncxDoc = itemNodeAttributes.getNamedItem("href").getNodeValue();
                }
              }
            }
          }
        }
      }
    }

    return isNCXdefined;
  }

  private void checkNcxDoc(String navDocEntry)
  {
    Document doc = docParser.parseDocument(navDocEntry);

    if (doc != null)
    {
      HashSet<String> tocLinkSet = new HashSet<String>();
      String ncxNS = "http://www.daisy.org/z3986/2005/ncx/";
      NodeList n = doc.getElementsByTagNameNS(ncxNS, "navPoint");
      for (int i = 0; i < n.getLength(); i++)
      {
        Element navElement = (Element) n.item(i);
        String playOrder = navElement.getAttributeNS(ncxNS, "playOrder");
        NodeList contentNodes = navElement.getElementsByTagNameNS(ncxNS, "content");
        if (contentNodes.getLength() > 0)
        {
          Element content = (Element) contentNodes.item(0);
          String path = content.getAttributeNS(ncxNS, "src");
          int hash = path.indexOf("#");
          if (hash >= 0)
          {
            path = path.substring(0, hash);
          }
          path = PathUtil.resolveRelativeReference(navDocEntry, path, null);

          if (!path.equals(""))
          {
            tocLinkSet.add(path);
            report.info(path, FeatureEnum.NAVIGATION_ORDER, playOrder);
          }
        }
      }

      PackageManifest manifest = epack.getManifest();
      PackageSpine spine = epack.getSpine();

      if (spine != null)
      {
        String tocFileName = spine.getToc();
        for (int i = 0; i < spine.itemsLength(); ++i)
        {
          SpineItem si = spine.getItem(i);
          ManifestItem mi = manifest.getItem(si.getIdref());
          if (mi != null)
          {
            String path = mi.getHref();
            path = PathUtil.resolveRelativeReference(navDocEntry, path,  null);

            if (path != null && !path.equals(tocFileName) && !path.equals(navDocEntry) && !tocLinkSet.contains(path))
            {
              report.message(MessageId.OPF_059, new MessageLocation(navDocEntry, -1, -1, path));
            }
          }
          else
          {
            // id not found in manifest
            report.message(MessageId.OPF_049, new MessageLocation(navDocEntry, -1, -1, epack.getPackageMainPath()), si.getIdref());
          }
        }
      }
    }
  }
}
