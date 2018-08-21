////////////////////////////////////////////////////////////////////////////////
//
// Copyright (c) 2015, Suncorp Metway Limited. All rights reserved.
//
// This is unpublished proprietary source code of Suncorp Metway Limited.
// The copyright notice above does not evidence any actual or intended
// publication of such source code.
//
////////////////////////////////////////////////////////////////////////////////
package au.com.suncorp.foundation.core.workflow.process;

import java.net.MalformedURLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jcr.LoginException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.osgi.framework.Constants;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.com.suncorp.foundation.controllers.dto.HostDTO;
import au.com.suncorp.foundation.core.utils.PublishResourceUtils;
import au.com.suncorp.foundation.core.workflow.process.email.HtmlTemplate;
import au.com.suncorp.foundation.core.workflow.process.email.Template;
import au.com.suncorp.foundation.core.workflow.process.email.TextTemplate;
import au.com.suncorp.foundation.services.impl.HostsFactory;

import com.day.cq.mailer.MessageGatewayService;
import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.HistoryItem;
import com.day.cq.workflow.exec.WorkItem;
import com.day.cq.workflow.exec.Workflow;
import com.day.cq.workflow.exec.WorkflowData;
import com.day.cq.workflow.exec.WorkflowProcess;
import com.day.cq.workflow.metadata.MetaDataMap;
import com.day.cq.workflow.model.WorkflowNode;

/**
 * This service is responsible to send the mail when content goes for review.
 *
 * @author mobans
 */

@Component (description = "Suncorp - Workflow Email Process", immediate = true, metatype = true, label = "Suncorp - Workflow Email Process")
@Service
@Properties ({
        @Property (name = Constants.SERVICE_DESCRIPTION, value = "Suncorp - Workflow Email Process"),
        @Property (name = "process.label", value = "Suncorp - Workflow Email Process"),
        @Property (name = "suncorp.wf.email.template",
                value = "/etc/workflow/notification/email/default/en.txt",
                label = "Default Email Template", description = "Email_Template"),
        @Property (name = "suncorp.wf.email.fromid", value = "",
                label = "From Email ID", description = "From Email ID"),
        @Property (name = "suncorp.wf.host.prefix",
                value = "http://localhost:4502", label = "Host URL Prefix",
                description = "Used to prefix links to pages in the notification emails"),
        @Property (name = "suncorp.wf.host.publish.prefix",
                value = "http://localhost:4503", label = "Publish/Dispatcher Host URL Prefix",
                description = "Used to prefix links to pages/images in the notification emails for AAMI"),
        @Property (name = "suncorp.wf.host.content.gio.prefix",
                value = "http://localhost:4503", label = "Content Host URL Prefix",
                description = "Used to prefix links to pages in the notification emails for GIO"),
        @Property (name = "suncorp.wf.host.dispatcher.gio.prefix",
                value = "http://localhost:4503",
                description = "Used to prefix links to pages/images in the notification emails for GIO"),
        @Property (name = "suncorp.wf.host.content.suncorp-insurance.prefix",
                value = "http://localhost:4502", label = "Host URL Prefix for Sun Insurance",
                description = "Used to prefix links to pages/images in the notification emails for Suncorp Insurance"),
        @Property (name = "suncorp.wf.host.publish.suncorp-insurance.prefix",
                value = "http://localhost:4503", label = "Publish Host URL Prefix for Sun Insurance",
                description = "Used to prefix links to pages/images in the notification emails for Suncorp Insurance"),
        @Property (name = "suncorp.wf.host.content.suncorp-bank.prefix",
                value = "http://localhost:4502", label = "Host URL Prefix for Sun Bank",
                description = "Used to prefix links to pages/images in the notification emails for Suncorp Bank"),
        @Property (name = "suncorp.wf.host.publish.suncorp-bank.prefix",
                value = "http://localhost:4503", label = "Publish Host URL Prefix for Sun Bank",
                description = "Used to prefix links to pages/images in the notification emails for Suncorp Bank"),
        @Property (name = "suncorp.wf.host.content.suncorp-corporate.prefix",
                value = "http://localhost:4502", label = "Host URL Prefix for Sun Corporate",
                description = "Used to prefix links to pages/images in the notification emails for Suncorp Corporate"),
        @Property (name = "suncorp.wf.host.publish.suncorp-corporate.prefix",
                value = "http://localhost:4503", label = "Publish Host URL Prefix for Suncorp Corporate",
                description = "Used to prefix links to pages/images in the notification emails for Suncorp Corporate"),
        @Property (name = "suncorp.wf.host.content.apia.prefix", value = "http://localhost:4502",
                label = "Host URL Prefix for APIA",
                description = "Used to prefix links to pages/images in the notification emails for APIA"),
        @Property (name = "suncorp.wf.host.publish.apia.prefix", value = "http://localhost:4503",
                label = "Publish Host URL Prefix for APIA",
                description = "Used to prefix links to pages/images in the notification emails for APIA"),
        @Property (name = "suncorp.wf.host.content.veroau.prefix", value = "http://localhost:4502",
                label = "Host URL Prefix for VERO AU",
                description = "Used to prefix links to pages/images in the notification emails for VERO AU"),
        @Property (name = "suncorp.wf.host.publish.veroau.prefix", value = "http://localhost:4503",
                label = "Publish Host URL Prefix for VERO AU",
                description = "Used to prefix links to pages/images in the notification emails for VERO AU")
})
public class SuncorpSendEmailProcess implements WorkflowProcess
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SuncorpSendEmailProcess.class);
    private static final String EMAIL_TEMLATE = "EMAIL_TEMLATE";
    private static final String JCR_PATH = "JCR_PATH";
    private static final String READ = "read";
    private static final String PROFILE_FIRST_NAME = "./profile/givenName";
    private static final String PROFILE_LAST_NAME = "./profile/familyName";
    private static final String PROFILE_EMAIL = "./profile/email";
    private static final String REJECT = "Reject";
    private String defaultTemplatePath = StringUtils.EMPTY;
    private String fromEmailId = StringUtils.EMPTY;
    private String hostPrefix = StringUtils.EMPTY;
    private String publishHostPrefix = StringUtils.EMPTY;
    private String gioContentHostPrefix = StringUtils.EMPTY;
    private String gioDispatcherHostPrefix = StringUtils.EMPTY;
    private String sunInsuranceHostPrefix = StringUtils.EMPTY;
    private String sunInsurancePublishHostPrefix = StringUtils.EMPTY;
    private String sunBankHostPrefix = StringUtils.EMPTY;
    private String sunBankPublishHostPrefix = StringUtils.EMPTY;
    private String sunCorporatePrefix = StringUtils.EMPTY;
    private String sunCorporatePublishHostPrefix = StringUtils.EMPTY;
    private String apiaContentHostPrefix = StringUtils.EMPTY;
    private String apiaPublishHostPrefix = StringUtils.EMPTY;
    private String veroauContentHostPrefix = StringUtils.EMPTY;
    private String veroauPublishHostPrefix = StringUtils.EMPTY;
    private String lineSeparator = "\n";
    private String memberName = StringUtils.EMPTY;

    private WorkflowSession wSession;
    /**
     * Reference to message Gateway Service class.
     */
    @Reference
    private MessageGatewayService messageGatewayService;

    /**
     * ResourceResolverFactory reference injected.
     */
    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private HostsFactory hostFactory;

    /**
     * Gets the service reference.
     *
     * @param context
     *            context
     */
    protected final void activate(final ComponentContext context)
    {
        LOGGER.info("SuncorpSendEmailProcess() - activate");
        @SuppressWarnings ("rawtypes")
        final Dictionary properties = context.getProperties();
        fromEmailId = (String) properties.get("suncorp.wf.email.fromid");
        defaultTemplatePath = (String) properties.get("suncorp.wf.email.template");
        hostPrefix = (String) properties.get("suncorp.wf.host.prefix");
        publishHostPrefix = (String) properties.get("suncorp.wf.host.publish.prefix");
        gioContentHostPrefix = (String) properties.get("suncorp.wf.host.content.gio.prefix");
        gioDispatcherHostPrefix = (String) properties.get("suncorp.wf.host.dispatcher.gio.prefix");
        sunInsuranceHostPrefix = (String) properties.get("suncorp.wf.host.content.suncorp-insurance.prefix");
        sunInsurancePublishHostPrefix = (String) properties.get("suncorp.wf.host.publish.suncorp-insurance.prefix");
        sunBankHostPrefix = (String) properties.get("suncorp.wf.host.content.suncorp-bank.prefix");
        sunBankPublishHostPrefix = (String) properties.get("suncorp.wf.host.publish.suncorp-bank.prefix");
        sunCorporatePrefix = (String) properties.get("suncorp.wf.host.content.suncorp-corporate.prefix");
        sunCorporatePublishHostPrefix = (String) properties.get("suncorp.wf.host.publish.suncorp-corporate.prefix");
        apiaContentHostPrefix = (String) properties.get("suncorp.wf.host.content.apia.prefix");
        apiaPublishHostPrefix = (String) properties.get("suncorp.wf.host.publish.apia.prefix");
        veroauContentHostPrefix = (String) properties.get("suncorp.wf.host.content.veroau.prefix");
        veroauPublishHostPrefix = (String) properties.get("suncorp.wf.host.publish.veroau.prefix");
    }

    /**
     * This method is used to execute the process.
     *
     * @param workItem
     *            WorkItem
     * @param workflowSession
     *            WorkflowSession
     * @param metaDataMap
     *            MetaDataMap
     * @throws WorkflowException
     *             WorkflowException
     */
    @Override
    public final void execute(WorkItem workItem, WorkflowSession workflowSession,
            MetaDataMap metaDataMap) throws WorkflowException
    {
        LOGGER.info("SuncorpSendEmailProcess Execute start");

        this.wSession = workflowSession;
        final Workflow workflow = workItem.getWorkflow();
        final WorkflowData workflowData = workflow.getWorkflowData();
        final String payLoad = getPayloadPath(workflowData);
        final List<Authorizable> recipients = getListOfRecipients(workItem, workflowSession, payLoad);
        final String templatePath = getTemplatePath(workItem);
        final String mailType = getEmailType(templatePath);
        final ValueMap properties = getWorkflowProperties(workItem, workflowData, payLoad);
        setResolvedPath(payLoad, properties);
        boolean sendEmail = isEmailToBeSent(workItem, workflowSession, metaDataMap, properties);
        if (sendEmail) {
            try {
                sendingMail(workItem, workflowSession, metaDataMap, recipients, templatePath, mailType, properties);
            } catch (EmailException | RepositoryException e) {
                LOGGER.error("Exception while sending mail", e);
                throw new WorkflowException(e.getMessage());
            }
        }

        LOGGER.info("SuncorpSendEmailProcess Execute End");
    }

    private void sendingMail(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap, final List<Authorizable> recipients,
            final String templatePath, final String mailType, final ValueMap properties) throws WorkflowException,
            EmailException,
            RepositoryException
    {
        setProperty(properties, "review.approval.jiraLink", getJiraLink(workItem, workflowSession, metaDataMap, properties));
        Template template = getTemplate(templatePath, mailType);
        boolean valueCheck = workItem.getNode().getTitle().contains(REJECT);
        boolean justInitiator =
                (!workItem.getNode().getMetaDataMap().containsKey("SENDTOAPPROVER") && workItem.getNode().getMetaDataMap()
                        .containsKey("SENDTOINITIATOR"));
        Authorizable owner = ownerName(workItem, workflowSession);
        setUserData(properties, owner, "participant.");

        if (checkCustomWorkflow(workItem, workflowSession) && !valueCheck && !justInitiator) {
            sendEmail(workItem, properties, template, getCCList(workItem, workflowSession));
        } else {
            sendEmail(workItem, recipients, properties, template);
        }
    }

    private Authorizable ownerName(WorkItem workItem, WorkflowSession workflowSession)
            throws WorkflowException, RepositoryException
    {
        Authorizable authorizableOwner = null;
        try {
            Map<String, Object> paramMap = new HashMap<String, Object>();
            paramMap.put(ResourceResolverFactory.SUBSERVICE, "readWriteService");
            ResourceResolver resourceResolver = this.resourceResolverFactory.getServiceResourceResolver(paramMap);
            UserManager userManager = resourceResolver.adaptTo(UserManager.class);
            String approverId = getActiveApprover(workItem, workflowSession);
            if (StringUtils.isEmpty(approverId)) {
                approverId = workItem.getWorkflow().getInitiator();
            }
            authorizableOwner = userManager.getAuthorizable(approverId);
        } catch (org.apache.sling.api.resource.LoginException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return authorizableOwner;
    }

    private List<String> getCCList(WorkItem workItem, WorkflowSession workflowSession) throws WorkflowException, RepositoryException
    {
        List<String> mailID = new ArrayList<String>();
        try {
            List<HistoryItem> history = workflowSession.getHistory(workItem
                    .getWorkflow());
            String groupName = StringUtils.EMPTY;

            for (HistoryItem historyItem : history) {
                if (historyItem.getWorkItem().getMetaDataMap()
                        .containsKey("group"))
                {
                    groupName = historyItem.getWorkItem().getMetaDataMap()
                            .get("group", StringUtils.EMPTY);
                    memberName = historyItem.getWorkItem().getMetaDataMap().get("member", StringUtils.EMPTY);
                    Map<String, Object> paramMap = new HashMap<String, Object>();
                    paramMap.put(ResourceResolverFactory.SUBSERVICE, "readWriteService");
                    ResourceResolver resourceresolver = this.resourceResolverFactory.getServiceResourceResolver(paramMap);
                    mailID = getMailIds(groupName, resourceresolver);
                }
            }
        } catch (org.apache.sling.api.resource.LoginException e) {
            LOGGER.error("Exception::", e);
        }
        return mailID;
    }

    private List<String> getMailIds(String groupName, ResourceResolver resourceresolver) throws RepositoryException
    {
        String mailID = StringUtils.EMPTY;

        List<String> allMailId = new ArrayList<String>();
        UserManager userManager = (UserManager) resourceresolver.adaptTo(UserManager.class);
        Authorizable authorizable = userManager.getAuthorizable(groupName);
        if (authorizable != null) {
            Group group = (Group) authorizable;
            Iterator<Authorizable> itr = group.getMembers();
            mailID = getMailIDCC(allMailId, userManager, itr);
            if (StringUtils.isNotBlank(mailID)) {
                mailID = mailID.substring(0, mailID.length() - 1);
            }
            allMailId.add(mailID);
        }
        return allMailId;
    }

    private String getMailIDCC(List<String> allMailId, UserManager userManager, Iterator<Authorizable> itr) throws RepositoryException
    {
        String mailIDTO = StringUtils.EMPTY;
        String mailIDCC = StringUtils.EMPTY;
        while (itr.hasNext())
        {
            Object obj = itr.next();
            if ((obj instanceof User)) {
                User user = (User) obj;
                Authorizable userAuthorizable = userManager.getAuthorizable(user.getID());
                if (StringUtils.isNotEmpty(getEmailAddress(userAuthorizable)))
                {
                    if (userAuthorizable.getID().equals(memberName)) {
                        mailIDTO = getEmailAddress(userAuthorizable);
                        allMailId.add(mailIDTO);
                    } else {
                        mailIDCC = mailIDCC.concat(getEmailAddress(userAuthorizable).trim().concat(","));
                    }
                }

            }
        }
        return mailIDCC;
    }

    private String getJiraLink(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap, ValueMap properties)
            throws WorkflowException
    {
        String jiraLink = StringUtils.EMPTY;
        List<HistoryItem> history = workflowSession.getHistory(workItem
                .getWorkflow());
        for (HistoryItem historyItem : history) {
            if (historyItem.getWorkItem().getMetaDataMap()
                    .containsKey("jiraLink"))
            {
                jiraLink = historyItem.getWorkItem().getMetaDataMap()
                        .get("jiraLink", StringUtils.EMPTY);
                break;
            }
        }
        final StringBuilder jiraLinkTrail = new StringBuilder();
        if (StringUtils.isNotEmpty(jiraLink)) {
            jiraLinkTrail.append("The change relates to this Card in JIRA: ").append("<a href=")
                    .append(jiraLink).append(">").append(jiraLink).append("</a>");
        }
        return jiraLinkTrail.toString();
    }

    private Boolean checkCustomWorkflow(WorkItem workItem, WorkflowSession workflowSession)
            throws WorkflowException
    {
        Boolean customWorkFLow = Boolean.FALSE;
        List<HistoryItem> history = workflowSession.getHistory(workItem
                .getWorkflow());
        for (HistoryItem historyItem : history) {

            if (historyItem.getWorkItem().getMetaDataMap()
                    .containsKey("overrideGroup@Delete"))
            {
                if (historyItem.getWorkItem().getMetaDataMap()
                        .containsKey("overrideGroup") &&
                        historyItem.getWorkItem().getMetaDataMap()
                                .get("overrideGroup").equals("true"))
                {
                    customWorkFLow = Boolean.TRUE;

                } else
                {
                    customWorkFLow = Boolean.FALSE;
                }
            }
        }

        return customWorkFLow;
    }

    private boolean isEmailToBeSent(final WorkItem workItem, final WorkflowSession workflowSession, final MetaDataMap metaDataMap,
            final ValueMap properties)
            throws WorkflowException
    {
        boolean sendEmail = false;
        if (isTimedEmailStep(metaDataMap)) {
            if (StringUtils.isNotEmpty(getScheduledTime(workItem, workflowSession))) {
                setProperty(properties, "scheduled.activation.time", getScheduledTime(workItem, workflowSession));
                sendEmail = true;
            }
        } else {
            sendEmail = true;
        }
        return sendEmail;
    }

    private boolean isTimedEmailStep(final MetaDataMap metaDataMap)
    {
        boolean isTimedEmailStep = false;
        String argument = metaDataMap.get("PROCESS_ARGS", StringUtils.EMPTY);
        if (StringUtils.isNotEmpty(argument)) {
            isTimedEmailStep = true;
        }
        return isTimedEmailStep;
    }

    private String getScheduledTime(final WorkItem workItem, final WorkflowSession workflowSession) throws WorkflowException
    {
        String activationDate = StringUtils.EMPTY;
        List<HistoryItem> history = workflowSession.getHistory(workItem
                .getWorkflow());
        for (HistoryItem historyItem : history) {
            if (historyItem.getWorkItem().getMetaDataMap()
                    .containsKey("activationDate"))
            {
                activationDate = historyItem.getWorkItem().getMetaDataMap()
                        .get("activationDate", StringUtils.EMPTY);
                break;
            }
        }
        return getDisplayDate(activationDate);
    }

    private String getDisplayDate(final String activationDate)
    {
        String returnDate = activationDate;
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                    .parse(activationDate);
            returnDate = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(date);
        } catch (ParseException e) {
            LOGGER.info("Error in getting correct date.");
        }
        return returnDate;
    }

    private Template getTemplate(final String templatePath, final String mailType)
    {
        Template template = null;
        if (StringUtils.equals(mailType, "html")) {
            lineSeparator = "<br/>";
            template = new HtmlTemplate((StringUtils.isEmpty(templatePath) ? this.defaultTemplatePath : templatePath), this.wSession.getSession());
        } else {
            template = new TextTemplate((StringUtils.isEmpty(templatePath) ? this.defaultTemplatePath : templatePath), this.wSession.getSession());
        }
        return template;
    }

    private void sendEmail(WorkItem workItem, final List<Authorizable> recipients, final ValueMap properties, Template template)
            throws EmailException
    {
        for (Authorizable recipient : recipients) {
            if (recipient != null) {
                final String emailId = getEmailAddress(recipient);
                if (StringUtils.isNotBlank(emailId)) {
                    final Email email = template.build(fromEmailId, emailId, properties);
                    messageGatewayService.getGateway(Email.class).send(email);
                }
            }
        }
    }

    private void sendEmail(WorkItem workItem, final ValueMap properties, Template template,
            final List<String> carbonCopyMailIds)
            throws EmailException
    {
        List<String> mailDetails = new ArrayList<String>();
        mailDetails = getMailIdDetails(carbonCopyMailIds);

        if (StringUtils.isNotBlank(mailDetails.get(0))) {

            final Email email = template.build(fromEmailId, mailDetails.get(0), properties);
            if (workItem.getNode().getMetaDataMap().containsKey("SENDTOINITIATOR"))
            {
                if (properties.containsKey("initiator.email"))
                {
                    email.addTo(properties.get("initiator.email").toString());
                }
            }
            if (StringUtils.isNotBlank(mailDetails.get(1)))
            {
                String[] mailIdList = mailDetails.get(1).split(",");
                for (String string : mailIdList)
                {
                    email.addCc(string);
                }
            }
            messageGatewayService.getGateway(Email.class).send(email);
        }
    }

    List<String> getMailIdDetails(List<String> string)
    {
        String emailId = StringUtils.EMPTY;
        String mailIdCC = StringUtils.EMPTY;
        List<String> mailDetails = new ArrayList<String>();
        if (string.size() == 1)
        {
            if (string.get(0).contains(","))
            {
                emailId = string.get(0).substring(0, string.get(0).indexOf(","));
                mailIdCC = string.get(0).substring(string.get(0).indexOf(",") + 1, string.get(0).length());
            } else
            {
                emailId = string.get(0);
            }
        } else
        {
            emailId = string.get(0);
            mailIdCC = string.get(1);
        }
        mailDetails.add(emailId);
        mailDetails.add(mailIdCC);
        return mailDetails;
    }

    private String getEmailType(final String templatePath)
    {
        String mailType = "txt";
        if (StringUtils.isNotBlank(templatePath)) {
            mailType = StringUtils.substringAfterLast(templatePath, ".");
        }
        return mailType;
    }

    private String getTemplatePath(final WorkItem workItem)
    {
        String templatePath = defaultTemplatePath;
        if (workItem.getNode().getMetaDataMap().containsKey(EMAIL_TEMLATE)) {
            templatePath = workItem.getNode().getMetaDataMap().get(EMAIL_TEMLATE, defaultTemplatePath);
        }
        return templatePath;
    }

    private String getPayloadPath(final WorkflowData workflowData)
    {
        String payLoad = StringUtils.EMPTY;
        if (JCR_PATH.equals(workflowData.getPayloadType())) {
            payLoad = workflowData.getPayload().toString();
        }
        return payLoad;
    }

    private List<Authorizable> getListOfRecipients(final WorkItem workItem, final WorkflowSession workflowSession, final String payLoad)
            throws WorkflowException
    {
        List<Authorizable> recipients = new ArrayList<Authorizable>();
        if (WorkflowNode.TYPE_PROCESS.equals(workItem.getNode().getType())) {
            recipients = getRecipientsForCustomSendMailProcessStep(workItem, workflowSession,
                    payLoad);
        } else {
            recipients = getRecipientsForSuncorpParticipantStep(workItem, workflowSession,
                    payLoad);
        }
        return recipients;
    }

    private List<Authorizable> getRecipientsForSuncorpParticipantStep(final WorkItem workItem,
            final WorkflowSession workflowSession, final String payLoad)
    {
        List<Authorizable> recipients = new ArrayList<Authorizable>();
        recipients.addAll(getWorkflowAssignes(workItem, workflowSession, payLoad));
        List<Authorizable> uniqueRecipients = new ArrayList<Authorizable>();
        List<String> uniqueEmailIds = new ArrayList<String>();
        if (recipients != null && !recipients.isEmpty()) {
            for (Authorizable recipient : recipients) {
                final String emailId = getEmailAddress(recipient);
                if (StringUtils.isNotBlank(emailId) && !uniqueEmailIds.contains(emailId)) {
                    uniqueRecipients.add(recipient);
                    uniqueEmailIds.add(emailId);
                }
            }
        }
        return uniqueRecipients;
    }

    private List<Authorizable> getRecipientsForCustomSendMailProcessStep(final WorkItem workItem,
            final WorkflowSession workflowSession, final String payLoad)
            throws WorkflowException
    {
        List<Authorizable> recipients = new ArrayList<Authorizable>();
        if (workItem.getNode().getMetaDataMap().containsKey("SENDTOINITIATOR")) {
            recipients.addAll(getWorkflowAssignees(workItem, workflowSession, payLoad, workItem.getWorkflow().getInitiator()));
        }
        if (workItem.getNode().getMetaDataMap().containsKey("SENDTOAPPROVER")) {
            recipients.addAll(getWorkflowAssignees(workItem, workflowSession, payLoad, getActiveApprover(workItem, workflowSession)));
        }
        if (workItem.getNode().getMetaDataMap().containsKey("PARTICIPANT")) {
            recipients.addAll(getWorkflowAssignees(workItem, workflowSession, payLoad,
                    workItem.getNode().getMetaDataMap().get("PARTICIPANT", StringUtils.EMPTY)));
        }
        return recipients;
    }

    /**
     * This method is used to get the BrandManagers list.
     *
     * @param item
     *            WorkItem
     * @param session
     *            WorkflowSession
     * @param path
     *            payLoad
     * @return brandManagers list
     */
    private List<Authorizable> getWorkflowAssignes(final WorkItem item, final WorkflowSession session, final String path)
    {
        LOGGER.info("START :: getBrandManagers");
        return getWorkflowAssignees(item, session, path, item.getCurrentAssignee());
    }

    /**
     * This method is used to get the BrandManagers list.
     *
     * @param item
     *            WorkItem
     * @param session
     *            WorkflowSession
     * @param path
     *            payLoad
     * @return brandManagers list
     */
    private List<Authorizable> getWorkflowAssignees(final WorkItem item, final WorkflowSession session, final String path,
            final String authorizableID)
    {
        LOGGER.info("START :: getWorkflowAssignees for {}", authorizableID);
        final List<Authorizable> assigneeList = new ArrayList<>();
        Authorizable brandManager;
        Session userSession = null;
        try {
            if (StringUtils.isNotEmpty(authorizableID)) {
                brandManager = this.getAuthorizable(authorizableID);
                if (brandManager.isGroup()) {
                    addGroupUsers(session, path, authorizableID, assigneeList);
                } else {
                    userSession = addUser(session, path, assigneeList, brandManager);
                }
            }
        } catch (RepositoryException e) {
            LOGGER.error("RepositoryException {}", e);
        } finally {

            if (userSession != null) {
                userSession.logout();
            }
        }
        return assigneeList;
    }

    private Session addUser(final WorkflowSession session, final String path, final List<Authorizable> assigneeList, Authorizable brandManager)
            throws LoginException, RepositoryException
    {
        Session userSession;
        userSession = session.getSession().impersonate(
                new SimpleCredentials(brandManager.getID(),
                        new char[0]));
        if (userSession.hasPermission(path, READ)) {
            assigneeList.add(brandManager);
        }
        return userSession;
    }

    private void addGroupUsers(final WorkflowSession session, final String path, final String authorizableID, final List<Authorizable> assigneeList)
            throws RepositoryException
    {
        Group group;
        group = (Group) this.getAuthorizable(authorizableID);
        final Iterator<Authorizable> users = group.getMembers();
        while (users.hasNext()) {
            addUsers(session, path, assigneeList,
                    users.next());
        }
    }

    /**
     * add users to list.
     *
     * @param session
     *            session
     * @param path
     *            path
     * @param brandManagers
     *            brandManagers
     * @param userSession
     *            userSession
     * @param users
     *            users
     */
    private void addUsers(final WorkflowSession session, final String path, final List<Authorizable> brandManagers, final Authorizable user)
    {
        Session userTempSession = null;
        try {
            if (!user.isGroup()) {
                userTempSession = session.getSession().impersonate(new SimpleCredentials(user.getID(), new char[0]));
                if (userTempSession != null && userTempSession.hasPermission(path, READ)) {
                    brandManagers.add(user);
                }
            } else {
                Group group = (Group) user;
                Iterator<Authorizable> iterator = group.getMembers();
                while (iterator.hasNext()) {
                    addUsers(session, path, brandManagers, iterator.next());
                }
            }
        } catch (RepositoryException e) {
            LOGGER.error("RepositoryException::", e);
        } finally {
            if (userTempSession != null) {
                userTempSession.logout();
            }
        }

    }

    public String getWorkflowComments(final WorkItem workItem)
    {
        StringBuilder comments = new StringBuilder();
        List<HistoryItem> history;
        try {
            history = this.wSession.getHistory(workItem.getWorkflow());
            for (HistoryItem historyItem : history) {
                if (historyItem.getWorkItem() != null && (WorkflowNode.TYPE_PARTICIPANT.equals(historyItem
                        .getWorkItem().getNode().getType()) || WorkflowNode.TYPE_DYNAMIC_PARTICIPANT
                        .equals(historyItem.getWorkItem().getNode()
                                .getType())))
                {
                    if (StringUtils.isNotEmpty(historyItem.getComment())) {
                        addCommentsToTrail(comments, historyItem);
                    }
                }
            }
        } catch (WorkflowException e) {
            LOGGER.info("WorkflowException :: {}" + e);
        }
        return comments.toString();
    }

    private void addCommentsToTrail(StringBuilder comments, HistoryItem historyItem)
    {
        String userName = this.getDisplayName(historyItem
                .getUserId());
        String displayDate = this.getDisplayDate(historyItem
                .getDate());
        comments.append("On [")
                .append(displayDate).append("] ").append(userName)
                .append(" said: ")
                .append(historyItem.getComment()).append(lineSeparator);
    }

    /**
     * Get Authorizable instance of provided user Id.
     *
     * @param userId
     * @return Authorizable inatance of provided user id.
     */
    public Authorizable getAuthorizable(final String userId)
    {
        String finalUserId = userId;
        // UserManager manager = this.getUserManager();
        if ((null != this.getUserManager()) && (null != userId)) {
            try {
                if ("system".equals(userId)) {
                    finalUserId = "admin";
                }
                return this.getUserManager().getAuthorizable(finalUserId);
            } catch (Exception e) {
                LOGGER.error("Exception - user manager did not find user - " + userId + ": ", e);
            }
        }
        return null;
    }

    private UserManager getUserManager()
    {
        try {
            final JackrabbitSession js = (JackrabbitSession) wSession.getSession();
            return js.getUserManager();
        } catch (Exception e) {
            LOGGER.error("Exception in getting userManager: ", e);
        }
        return null;
    }

    /**
     * Read and Return User's Email property.
     *
     * @param user
     * @return
     */
    public String getEmailAddress(final Authorizable user)
    {
        String mail = StringUtils.EMPTY;
        try {
            if (user != null && user.getProperty(PROFILE_EMAIL) != null && user.getProperty(PROFILE_EMAIL).length > 0) {
                mail = user.getProperty(PROFILE_EMAIL)[0].getString();
            }
        } catch (RepositoryException e) {
            LOGGER.error("Error in getting email address", e);
        }
        return mail;
    }

    public String getHostPrefix()
    {
        return this.hostPrefix;
    }

    public String getPublishHostPrefix()
    {
        return this.publishHostPrefix;
    }

    public String getFromEmailID()
    {
        return this.fromEmailId;
    }

    private String getDisplayDate(final Date date)
    {
        String returnDate = StringUtils.EMPTY;
        try {
            returnDate = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(date);
        } catch (Exception e) {
            LOGGER.info("Error in getting display date.", e);
        }
        return returnDate;
    }

    private String getDisplayName(final String userId)
    {
        StringBuilder displayName = new StringBuilder();
        Authorizable auth = this.getAuthorizable(userId);
        try {
            if (auth.hasProperty(PROFILE_FIRST_NAME)) {
                displayName.append(auth.getProperty(PROFILE_FIRST_NAME)[0]
                        .toString()).append(" ");
            }
            if (auth.hasProperty(PROFILE_LAST_NAME)) {
                displayName.append(auth.getProperty(PROFILE_LAST_NAME)[0]
                        .toString()).append(" ");
            }
        } catch (RepositoryException e) {
            LOGGER.info("Error in getting user name.", e);
        }
        displayName.append("(").append(userId).append(")");
        return displayName.toString();
    }

    private String getActiveApprover(final WorkItem workItem,
            final WorkflowSession workflowSession) throws WorkflowException
    {
        final List<HistoryItem> history = workflowSession.getHistory(workItem
                .getWorkflow());
        String retValue = StringUtils.EMPTY;
        for (HistoryItem historyItem : history) {
            if (isApproverStep(historyItem)) {
                retValue = historyItem.getUserId();
            }
        }
        return retValue;
    }

    private boolean isApproverStep(final HistoryItem historyItem)
    {
        return historyItem.getWorkItem() != null && WorkflowNode.TYPE_PARTICIPANT.equals(historyItem
                .getWorkItem().getNode().getType()) && !StringUtils.equals("WorkItemDelegated",
                historyItem.getAction()) &&
                historyItem.getWorkItem().getNode().getMetaDataMap()
                        .get("APPROVER_STEP_FLAG") != null;
    }

    private ValueMap getWorkflowProperties(WorkItem workItem, final WorkflowData workflowData, String payLoad)
    {
        ValueMap properties = new ValueMapDecorator(new HashMap<String, Object>());
        if (workItem != null) {
            setProperty(properties, "event.TimeStamp", workItem.getTimeStarted().toString());
            setItemProperties(workItem, properties);
            setInstanceProperties(workItem, properties);
            setModelProperties(workItem, properties);
            setAllProperties(properties, workflowData.getMetaDataMap(), "data");
            setPayloadProperties(workflowData, payLoad, properties);
            setProperty(properties, "item.workflow.history", getWorkflowComments(workItem));
            properties = processHosts(payLoad, properties);
            setHostProperties(properties);
            setUserData(properties, getAuthorizable(workItem.getWorkflow().getInitiator()), "initiator.");
        }
        return properties;
    }

    private void setHostProperties(ValueMap properties)
    {
        setProperty(properties, "host.prefix", hostPrefix);
        setProperty(properties, "host.publish.prefix", publishHostPrefix);
        setProperty(properties, "host.content.gio.prefix", gioContentHostPrefix);
        setProperty(properties, "host.dispatcher.gio.prefix", gioDispatcherHostPrefix);
        setProperty(properties, "suncorpinsurance.host.prefix", sunInsuranceHostPrefix);
        setProperty(properties, "suncorpinsurance.publish.host.prefix", sunInsurancePublishHostPrefix);
        setProperty(properties, "suncorpbank.host.prefix", sunBankHostPrefix);
        setProperty(properties, "suncorpbank.publish.host.prefix", sunBankPublishHostPrefix);
        setProperty(properties, "suncorpcorporate.host.prefix", sunCorporatePrefix);
        setProperty(properties, "suncorpcorporate.publish.host.prefix", sunCorporatePublishHostPrefix);
        setProperty(properties, "suncorp.wf.host.content.apia.prefix", apiaContentHostPrefix);
        setProperty(properties, "suncorp.wf.host.publish.apia.prefix", apiaPublishHostPrefix);
        setProperty(properties, "suncorp.wf.host.content.veroau.prefix", veroauContentHostPrefix);
        setProperty(properties, "suncorp.wf.host.publish.veroau.prefix", veroauPublishHostPrefix);

    }

    private void setResolvedPath(String payLoad, ValueMap properties)
    {

        try {
            setProperty(properties, "resolvedPayloadPath", PublishResourceUtils.getResolvedPathFromPublish(payLoad));
        } catch (MalformedURLException e) {
            LOGGER.error("Publish URL is not valid ", e);
        }
    }

    private ValueMap processHosts(String payLoad, ValueMap properties)
    {
        Map<String, HostDTO> map = hostFactory.getHostDTOMap();
        if (map != null) {
            for (String pathId : map.keySet()) {
                if (StringUtils.contains(payLoad, pathId)) {
                    setProperty(properties, "host.author.prefix", map.get(pathId).getAuthorPrefix());
                    setProperty(properties, "host.domain.prefix", map.get(pathId).getDomainPrefix());
                    setProperty(properties, "host.logo.link", map.get(pathId).getLogoLink());
                    break;
                }
            }
        }
        return properties;
    }

    private void setPayloadProperties(final WorkflowData workflowData, String payLoad, final ValueMap properties)
    {
        setProperty(properties, "payload.data", workflowData.getPayload());
        setProperty(properties, "payload.type", workflowData.getPayloadType());
        setProperty(properties, "payload.path", payLoad);
    }

    private void setModelProperties(WorkItem workItem, final ValueMap properties)
    {
        setProperty(properties, "model.title", workItem.getWorkflow().getWorkflowModel().getTitle());
        setProperty(properties, "model.id", workItem.getWorkflow().getWorkflowModel().getId());
        setProperty(properties, "model.version", workItem.getWorkflow().getWorkflowModel().getVersion());
        setAllProperties(properties, workItem.getWorkflow().getWorkflowModel().getMetaDataMap(), "model.data.");
    }

    private void setInstanceProperties(WorkItem workItem, final ValueMap properties)
    {
        setProperty(properties, "instance.id", workItem.getWorkflow().getId());
        setProperty(properties, "instance.state", workItem.getWorkflow().getState());
        setAllProperties(properties, workItem.getWorkflow().getMetaDataMap(), "instance.data.");
    }

    private void setItemProperties(WorkItem workItem, final ValueMap properties)
    {
        setProperty(properties, "item.id", workItem.getId());
        setProperty(properties, "item.node.id", workItem.getNode().getId());
        setProperty(properties, "item.node.title", workItem.getNode().getTitle());
        setProperty(properties, "item.node.type", workItem.getNode().getType());
        setAllProperties(properties, workItem.getNode().getMetaDataMap(), "item.node.data.");
        setAllProperties(properties, workItem.getWorkflowData().getMetaDataMap(), "item.workflow.data.");
        setAllProperties(properties, workItem.getMetaDataMap(), "item.data.");

    }

    private void setProperty(final ValueMap properties, final String name, final Object value)
    {
        if (value != null) {
            properties.put(name, value);
        }
    }

    private void setAllProperties(final ValueMap properties, final MetaDataMap metaData, final String prefix)
    {
        final Iterator<String> names = metaData.keySet().iterator();
        while (names.hasNext()) {
            String name = (String) names.next();
            properties.put(prefix + name, metaData.get(name, String.class));
        }
    }

    private void setUserData(final ValueMap properties, final Authorizable auth, final String prefix)
    {
        final Iterator<String> iter;
        try {
            iter = auth.getPropertyNames();
            putUserProperties(properties, auth, prefix, iter);

            properties.put(prefix + "id", auth.getID());
            properties.put(prefix + "name",
                    (auth.hasProperty(PROFILE_FIRST_NAME) ? auth.getProperty(PROFILE_FIRST_NAME)[0].toString() + " " : StringUtils.EMPTY) +
                            (auth.hasProperty(PROFILE_LAST_NAME) ? auth.getProperty(PROFILE_LAST_NAME)[0].toString() : "-"));
            putUserEmail(properties, auth, prefix);
            putUserFamilyName(properties, auth, prefix);
            putUserGivenName(properties, auth, prefix);
        } catch (RepositoryException e) {
            LOGGER.error("Error occurred while getting data for user.", e);
        }
    }

    private void putUserGivenName(final ValueMap properties, final Authorizable auth, final String prefix) throws RepositoryException
    {
        if (auth.hasProperty(PROFILE_FIRST_NAME)) {
            properties.put(prefix + "givenName", auth.getProperty(PROFILE_FIRST_NAME)[0].toString());
        }
    }

    private void putUserFamilyName(final ValueMap properties, final Authorizable auth, final String prefix) throws RepositoryException
    {
        if (auth.hasProperty(PROFILE_LAST_NAME)) {
            properties.put(prefix + "familyName", auth.getProperty(PROFILE_LAST_NAME)[0].toString());
        }
    }

    private void putUserEmail(final ValueMap properties, final Authorizable auth, final String prefix) throws RepositoryException
    {
        if (auth.hasProperty(PROFILE_EMAIL)) {
            properties.put(prefix + "email", auth.getProperty(PROFILE_EMAIL)[0].toString());
        }
    }

    private void putUserProperties(final ValueMap properties, final Authorizable auth, final String prefix, final Iterator<String> iter)
            throws RepositoryException
    {
        while (iter.hasNext()) {
            final String name = (String) iter.next();
            properties.put(prefix + name, auth.getProperty(name));
        }
    }
}
