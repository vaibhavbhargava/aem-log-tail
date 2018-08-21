////////////////////////////////////////////////////////////////////////////////
//
// Copyright (c) 2015, Suncorp Metway Limited. All rights reserved.
//
// This is unpublished proprietary source code of Suncorp Metway Limited.
// The copyright notice above does not evidence any actual or intended
// publication of such source code.
//
////////////////////////////////////////////////////////////////////////////////

package au.com.suncorp.foundation.controllers.servlet.sitemap;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Iterator;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.day.cq.commons.Externalizer;
import com.day.cq.wcm.api.Page;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SitemapGenerator
{

    static class XmlElement
    {
        private String startElement;
        private String nameSpacePrefix;
        private String characters;
        private Map<String, String> attributes;

        public String getStartElement()
        {
            return startElement;
        }

        public void setStartElement(String startElement)
        {
            this.startElement = startElement;
        }

        public String getCharacters()
        {
            return characters;
        }

        public void setCharacters(String characters)
        {
            this.characters = characters;
        }

        public Map<String, String> getAttributes()
        {
            if (attributes == null) {
                return new HashMap<String, String>();
            }
            return attributes;
        }

        public void setAttributes(Map<String, String> attributes)
        {
            this.attributes = attributes;
        }

        public String getNameSpacePrefix()
        {
            return nameSpacePrefix;
        }

        public void setNameSpacePrefix(String nameSpacePrefix)
        {
            this.nameSpacePrefix = nameSpacePrefix;
        }
    }

    private class SitemapElement
    {
        private Calendar lastMod;
        private String priority = StringUtils.EMPTY;
        private String changeFrequency = StringUtils.EMPTY;
        private Boolean displayInSitemap = false;
        private List<XmlElement> customTagsValues;

        public List<XmlElement> getCustomTagsValues()
        {
            if (customTagsValues == null) {
                customTagsValues = new ArrayList<>();
            }
            return customTagsValues;
        }

        public void setCustomTagsValues(List<XmlElement> customTagsValues)
        {
            this.customTagsValues = customTagsValues;
        }

        public Calendar getLastMod()
        {
            return lastMod;
        }

        public void setLastMod(final Calendar date)
        {
            lastMod = date;
        }

        public String getPriority()
        {
            return priority;
        }

        public void setPriority(final String priority)
        {
            this.priority = priority;
        }

        public String getChangeFrequency()
        {
            return changeFrequency;
        }

        public void setChangeFrequency(final String changeFrequency)
        {
            this.changeFrequency = changeFrequency;
        }

        public Boolean getDisplayInSitemap()
        {
            return displayInSitemap;
        }

        public void setDisplayInSitemap(final Boolean displayInSitemap)
        {
            this.displayInSitemap = displayInSitemap;
        }
    }

    public static final String URLSET_TAG = "urlset";
    public static final String XMLNS = "xmlns";
    public static final String NS_VALUE = "http://www.sitemaps.org/schemas/sitemap/0.9";
    private static final Logger LOGGER = LoggerFactory.getLogger(SitemapGenerator.class);

    protected ResourceResolver resourceResolver;
    protected Externalizer externalizer;

    private String loc;
    private XmlElement nameSpace;

    public SitemapGenerator(Externalizer externalizer, ResourceResolver resourceResolver)
    {
        this.externalizer = externalizer;
        this.resourceResolver = resourceResolver;
    }

    // override this method to handle complex custom tags i.e. mytag=${myvalue}
    protected List<XmlElement> processCustomTags(String[] tags)
    {
        List<XmlElement> result = new ArrayList();

        if (tags != null) {
            for (String values : tags) {
                String[] keyValue = values.split("=");
                XmlElement value = new XmlElement();
                if (keyValue != null && keyValue.length > 1 && !keyValue[1].isEmpty() && !keyValue[1].equals("\"\"")) {
                    value.setStartElement(keyValue[0]);
                    value.setCharacters(keyValue[1]);
                } else {
                    value.setStartElement(keyValue[0]);
                }
                result.add(value);
            }
        }

        return result;
    }

    public void setNameSpace(XmlElement nameSpace)
    {
        this.nameSpace = nameSpace;
    }

    protected void writeNameSpace(XMLStreamWriter stream) throws XMLStreamException
    {
        if (nameSpace != null) {
            stream.writeNamespace(XMLNS, nameSpace.getNameSpacePrefix());
            for (Map.Entry<String, String> item : nameSpace.getAttributes().entrySet()) {
                stream.writeAttribute(item.getKey(), item.getValue());
            }
        }
    }

    public void setLoc(String externalizerDomain)
    {
        loc = externalizer.externalLink(resourceResolver,
                externalizerDomain, "");
    }

    protected <T> T getPropertyValue(Class<T> clazz, String propName, Node node, T defaultValue) throws RepositoryException
    {
        Object result = defaultValue;

        if (node.hasProperty(propName)) {
            if (clazz.getName().equals(Boolean.class.getName())) {
                result = clazz.cast(node.getProperty(propName).getBoolean());
            } else if (clazz.getName().equals(Calendar.class.getName())) {
                Object d = node.getProperty(propName).getDate();
                result = clazz.cast(node.getProperty(propName).getDate());
            } else if (clazz.getName().equals(Integer.class.getName())) {
                result = clazz.cast(node.getProperty(propName).getLong());
            } else {
                result = clazz.cast(node.getProperty(propName).getString());
            }
        }

        return (T) result;
    }

    private SitemapElement getPageProperties(final Page page, String[] customTags)
    {
        SitemapElement result = new SitemapElement();
        try {
            final Resource res = resourceResolver.getResource(page.getPath() + "/jcr:content");
            if (res != null) {
                final Node node = res.adaptTo(Node.class);
                result.setPriority(this.getPropertyValue(String.class, "priority", node, ""));
                result.setChangeFrequency(this.getPropertyValue(String.class, "changefrequency", node, ""));
                result.setLastMod(this.getPropertyValue(Calendar.class, "cq:lastModified", node, null));
                result.setDisplayInSitemap(this.getPropertyValue(Boolean.class, "displayInSitemap", node, false));
                result.setCustomTagsValues(processCustomTags(customTags));
            }
        } catch (final RepositoryException e) {
            LOGGER.error("Error retrieving node property.", e);
        }
        return result;
    }

    private void writePageProperties(Page rootPage, String[] customTags, String externalizerDomain,
            XMLStreamWriter stream, boolean includeSelf, boolean isInitial) throws XMLStreamException
    {
        SitemapElement elem = getPageProperties(rootPage, customTags);
        if (isInitial) {
            write(stream, loc, elem);
        }
        if (externalizerDomain.equals("suncorp-bank") || externalizerDomain.equals("suncorp-insurance") || includeSelf) {
            loc = externalizer.externalLink(resourceResolver, externalizerDomain,
                    String.format("%s.html", rootPage.getPath()));
            write(stream, loc, elem);
        }
        for (final Iterator<Page> children = rootPage.listChildren(null, true); children.hasNext();) {
            final Page childPage = children.next();
            elem = this.getPageProperties(childPage, customTags);
            if (elem.getDisplayInSitemap() == true) {
                loc = externalizer.externalLink(resourceResolver, externalizerDomain,
                        String.format("%s.html", childPage.getPath()));
                write(stream, loc, elem);
            }
        }
    }

    protected void closeStream(XMLStreamWriter stream)
    {
        if (stream != null) {
            try {
                stream.close();
            } catch (XMLStreamException e) {
                LOGGER.debug("Stream closed.");
            }
        }
    }

    public void generateSitemap(Page rootPage, PrintWriter writer, String[] customTags,
            String externalizerDomain)
    {
        final XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();
        XMLStreamWriter stream = null;
        try {
            stream = outputFactory.createXMLStreamWriter(writer);
            stream.writeStartDocument("1.0");
            stream.writeStartElement("", URLSET_TAG, NS_VALUE);
            this.writeNameSpace(stream);
            this.writePageProperties(rootPage, customTags, externalizerDomain, stream, false, true);
            stream.writeEndDocument();
        } catch (XMLStreamException e) {
            LOGGER.error("Error retrieving sitemap page properties.", e);
        } finally {
            this.closeStream(stream);
        }
    }

    public void generateSitemap(List<Page> rootPages, PrintWriter writer, String[] customTags,
            String externalizerDomain)
    {
        final XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();
        XMLStreamWriter stream = null;
        try {
            stream = outputFactory.createXMLStreamWriter(writer);
            stream.writeStartDocument("1.0");
            stream.writeStartElement("", URLSET_TAG, NS_VALUE);
            this.writeNameSpace(stream);
            boolean isInitial = true;
            for (Page rootPage : rootPages) {
                this.writePageProperties(rootPage, customTags, externalizerDomain, stream, true, isInitial);
                isInitial = false;
            }
            stream.writeEndDocument();
        } catch (XMLStreamException e) {
            LOGGER.error("Error retrieving sitemap page properties.", e);
        } finally {
            this.closeStream(stream);
        }
    }

    protected void writeCustomTags(XMLStreamWriter stream, List<XmlElement> elem) throws XMLStreamException
    {
        // process custom tags
        for (XmlElement element : elem) {
            if (StringUtils.isNotBlank(element.getStartElement())) {
                if (StringUtils.isNotBlank(element.getCharacters())) {
                    stream.writeStartElement(element.getStartElement());
                    stream.writeCharacters(element.getCharacters());
                    stream.writeEndElement();
                } else {
                    stream.writeEmptyElement(element.getStartElement());
                }
            }
        }
    }

    private void write(final XMLStreamWriter stream, String loc, SitemapElement elem) throws XMLStreamException
    {
        stream.writeStartElement(NS_VALUE, "url");
        stream.writeStartElement(NS_VALUE, "loc");
        stream.writeCharacters(loc);
        stream.writeEndElement();
        if (elem.getLastMod() != null) {
            final Calendar lastModifiedDate = elem.getLastMod();
            final SimpleDateFormat sdfDestination = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ssXXX");
            stream.writeStartElement(NS_VALUE, "lastmod");
            stream.writeCharacters(sdfDestination
                    .format(lastModifiedDate.getTime()));
            stream.writeEndElement();
        }
        if (StringUtils.isNotBlank(elem.getChangeFrequency())) {
            stream.writeStartElement(NS_VALUE, "changefreq");
            stream.writeCharacters(elem.getChangeFrequency());
            stream.writeEndElement();
        }
        if (StringUtils.isNotBlank(elem.getPriority())) {
            stream.writeStartElement(NS_VALUE, "priority");
            stream.writeCharacters(elem.getPriority());
            stream.writeEndElement();
        }
        this.writeCustomTags(stream, elem.getCustomTagsValues());
        stream.writeEndElement();
    }

}
