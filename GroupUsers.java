////////////////////////////////////////////////////////////////////////////////
//
// Copyright (c) 2015, Suncorp Metway Limited. All rights reserved.
//
// This is unpublished proprietary source code of Suncorp Metway Limited.
// The copyright notice above does not evidence any actual or intended
// publication of such source code.
//
////////////////////////////////////////////////////////////////////////////////
package au.com.suncorp.foundation.servlets;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.settings.SlingSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import au.com.suncorp.foundation.core.utils.Constants;

@Component (immediate = true, metatype = true, label = "Suncorp Foundation - Core - WorkflowDialogServlet",
        description = "Servlet for workflow dialog")
@Service (serviceFactory = false, value = { Servlet.class })
@Properties ({ @org.apache.felix.scr.annotations.Property (name = "sling.servlet.methods", value = { "GET" }),
        @org.apache.felix.scr.annotations.Property (name = "sling.servlet.paths", value = { "/sunapps/groupusers" }) })
public class GroupUsers
        extends SlingAllMethodsServlet

{

    private static final long serialVersionUID = 5838709365895130064L;
    private static final Logger LOGGER = LoggerFactory.getLogger(GroupUsers.class);
    @Reference
    private ResourceResolverFactory resourceResolverFactory;
    @Reference
    private transient SlingSettingsService slingSettingsService;

    public void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException
    {

        String groupName = request.getParameter("groupname");
        Boolean isAuthor = slingSettingsService.getRunModes().contains("author");
        if (isAuthor)
        {
            try
            {
                JSONArray membersArray = getMemberArray(groupName);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(membersArray.toString());
            } catch (LoginException | RepositoryException | JSONException e)
            {
                LOGGER.error("Exception in group user servlet", e.getMessage());
            }
        }
    }

    private JSONArray getMemberArray(String groupName) throws LoginException, RepositoryException, JSONException
    {
        JSONArray membersArray = new JSONArray();
        Map<String, Object> paramMap = new HashMap<String, Object>();
        paramMap.put(ResourceResolverFactory.SUBSERVICE, "readWriteService");
        ResourceResolver resourceresolver = this.resourceResolverFactory.getServiceResourceResolver(paramMap);
        UserManager userManager = (UserManager) resourceresolver.adaptTo(UserManager.class);
        Authorizable authorizable = userManager.getAuthorizable(groupName);
        if (authorizable != null)
        {
            Group group = (Group) authorizable;

            Iterator<Authorizable> itr = group.getMembers();
            while (itr.hasNext())
            {
                iterateGroupMembers(membersArray, itr);
            }
        }
        return membersArray;
    }

    private void iterateGroupMembers(JSONArray membersArray, Iterator<Authorizable> itr) throws JSONException, RepositoryException
    {
        JSONObject userDetailsJson;
        userDetailsJson = new JSONObject();
        Object obj = itr.next();
        if ((obj instanceof User))
        {
            User user = (User) obj;

            if (user.getID() != null)
            {
                membersArray.put(getMemberList(userDetailsJson, user));
            }
        }
    }

    private JSONObject getMemberList(JSONObject userDetailsJson, User user) throws ValueFormatException, RepositoryException,
            JSONException
    {

        String userName = StringUtils.EMPTY;

        if (user.hasProperty("./profile/givenName") || user.hasProperty("./profile/familyName"))
        {
            userName = fetchFirstLastName(user);
        }
        if (StringUtils.isEmpty(userName.trim()))
        {
            userName = user.getID();
        }
        userDetailsJson.put("text", userName);
        userDetailsJson.put("value", user.getID());
        return userDetailsJson;
    }

    private String fetchFirstLastName(User user) throws RepositoryException, ValueFormatException
    {
        String uName = StringUtils.EMPTY;
        String firstName = StringUtils.EMPTY;
        String lastName = StringUtils.EMPTY;
        if (user.hasProperty("./profile/givenName") && user.getProperty("./profile/givenName").length > 0)
        {
            firstName = user.getProperty("./profile/givenName")[0].getString() + Constants.SPACE.getValue();
        }
        if (user.hasProperty("./profile/familyName") && user.getProperty("./profile/familyName").length > 0)
        {
            lastName = user.getProperty("./profile/familyName")[0].getString();
        }
        uName = firstName + lastName;
        return uName;
    }

    protected void bindResourceResolverFactory(ResourceResolverFactory paramResourceResolverFactory)
    {
        this.resourceResolverFactory = paramResourceResolverFactory;
    }

    protected void unbindResourceResolverFactory(ResourceResolverFactory paramResourceResolverFactory)
    {
        if (this.resourceResolverFactory == paramResourceResolverFactory) {
            this.resourceResolverFactory = null;
        }
    }
}
