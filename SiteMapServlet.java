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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.io.IOException;

import javax.servlet.ServletException;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.commons.Externalizer;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.PropertyUnbounded;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component (metatype = true, label = "Suncorp Foundation - Core - Site Map Servlet",
        description = "Site Map Servlet", configurationFactory = true)
@Service
@SuppressWarnings ("serial")
@Properties ({
        @Property (name = "sling.servlet.resourceTypes", unbounded = PropertyUnbounded.ARRAY,
                label = "Sling Resource Type",
                description = "Sling Resource Type for the Home Page component or components."),
        @Property (name = "sling.servlet.selectors", value = "sitemap", propertyPrivate = true),
        @Property (name = "sling.servlet.extensions", value = "xml", propertyPrivate = true),
        @Property (name = "sling.servlet.methods", value = "GET", propertyPrivate = true) })
public class SiteMapServlet extends SlingSafeMethodsServlet
{
    private static final String DEFAULT_EXTERNALIZER_DOMAIN = "aami";
    private static final Logger LOGGER = LoggerFactory.getLogger(SiteMapServlet.class);

    @Property (value = DEFAULT_EXTERNALIZER_DOMAIN, label = "Externalizer Domain",
            description = "Must correspond to a configuration of the Externalizer component.")
    private static final String PROP_EXTERNALIZER_DOMAIN = "externalizer.domain";
    @Property (unbounded = PropertyUnbounded.ARRAY,
            description = "Custom tags key=value format. i.e. mytag=myvalue. result: <mytag>myvalue</mytag>")
    private static final String PROP_CUSTOM_TAG = "customTags";
    @Property (boolValue = true, description = "Generate video sitemap.")
    private static final String PROP_GENERATE_VIDEO_SITEMAP = "isVideoSitemapEnabled";
    @Property (unbounded = PropertyUnbounded.ARRAY,
            description = "Provide content paths to be included as roots for sitemap data generation " +
                    "in aggregated way, this would override current Resource usage for sitemap and would " +
                    "use all the listed resources rather..", label = "Custom Aggregated Paths")
    private static final String PROP_AGGREGATED_PATHS = "sitemap.aggregates";

    @Reference
    protected Externalizer externalizer;

    private String[] customTags;
    private boolean isVideoSitemapEnabled;

    private String externalizerDomain;

    private String[] rootPages = null;

    // override this method to customize the namespaces
    protected SitemapGenerator.XmlElement getNameSpace()
    {
        SitemapGenerator.XmlElement nameSpace = new SitemapGenerator.XmlElement();
        nameSpace.setNameSpacePrefix(SitemapGenerator.NS_VALUE);

        return nameSpace;
    }

    @Activate
    protected void activate(final Map<String, Object> properties)
    {
        externalizerDomain = StringUtils.isNotEmpty(properties.get(PROP_EXTERNALIZER_DOMAIN).toString()) ? properties
                .get(PROP_EXTERNALIZER_DOMAIN).toString()
                : DEFAULT_EXTERNALIZER_DOMAIN;

        customTags = PropertiesUtil.toStringArray(properties.get(PROP_CUSTOM_TAG));
        rootPages = PropertiesUtil.toStringArray(properties.get(PROP_AGGREGATED_PATHS));
        isVideoSitemapEnabled = PropertiesUtil.toBoolean(properties.get(PROP_GENERATE_VIDEO_SITEMAP), true);
    }

    protected SitemapGenerator getSitemapGenerator(ResourceResolver rr)
    {
        return new SitemapGenerator(externalizer, rr);
    }

    @Override
    protected void doGet(final SlingHttpServletRequest request,
            final SlingHttpServletResponse response) throws ServletException,
            IOException
    {

        response.setContentType(request.getResponseContentType());
        final ResourceResolver resourceResolver = request.getResourceResolver();
        final PageManager pageManager = resourceResolver
                .adaptTo(PageManager.class);

        final Page page = pageManager.getContainingPage(request.getResource());
        SitemapGenerator sitemapHelper = this.getSitemapGenerator(request.getResourceResolver());
        sitemapHelper.setLoc(externalizerDomain);
        sitemapHelper.setNameSpace(this.getNameSpace());
        List<Page> rootPageList = new ArrayList<Page>();
        getRootPagesList(request, rootPageList);
        try {
            if (rootPageList.size() > 0) {
                sitemapHelper.generateSitemap(rootPageList, response.getWriter(), customTags, externalizerDomain);
            } else {
                sitemapHelper.generateSitemap(page, response.getWriter(), customTags, externalizerDomain);
            }

        } catch (Exception e) {
            LOGGER.error("Error generating sitemap files.", e);
        }
    }

    private void getRootPagesList(final SlingHttpServletRequest request, List<Page> rootPageList)
    {
        if (rootPages != null && rootPages.length > 0) {
            for (String s : rootPages) {
                Resource pageResource = request.getResourceResolver().resolve(s);
                if (pageResource != null && pageResource.adaptTo(Page.class) != null) {
                    final Page rootPage = pageResource.adaptTo(Page.class);
                    rootPageList.add(rootPage);
                }
            }
        }
    }
}
