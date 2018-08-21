////////////////////////////////////////////////////////////////////////////////
//
// Copyright (c) 2018, Suncorp Metway Limited. All rights reserved.
//
// This is unpublished proprietary source code of Suncorp Metway Limited.
// The copyright notice above does not evidence any actual or intended
// publication of such source code.
//
////////////////////////////////////////////////////////////////////////////////package au.com.suncorp.foundation.core.workflow.process;
package au.com.suncorp.foundation.core.workflow.process;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Calendar;
import java.util.Dictionary;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFormatException;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.EmailAttachment;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.MultiPartEmail;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.mailer.MessageGatewayService;
import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;
import com.day.cq.workflow.exec.WorkflowProcess;
import com.day.cq.workflow.metadata.MetaDataMap;

@Component (description = "RedirectMap Change Review Process", immediate = true, metatype = true,
        label = "RedirectMap Change Review Process")
@Service
@Properties ({
        @Property (name = "process.label", value = "RedirectMap Change Review Process"),
        @Property (label = "Sender Mail", name = "redirectmapChangeReview.sender",
                description = "Mail Id from which the email must be triggerred",
                value = "vaibhav.bhargava@suncorp.com.au"),
        @Property (label = "Receivers", name = "redirectmapChangeReview.receivers",
                value = "Neha.Thapliyal@suncorp.com.au;vaibhavbhargava@suncorp.com.au",
                description = "Receiver List, ';' separated.") })
public class RedirectMapReviewProcess implements WorkflowProcess
{

    private static final String TAB_SPACE = "\t";
    private static final String NEW_LINE = "\n";
    private static final String JCR_DATA = "jcr:data";
    private static final String SEMICOLON_DELIMITER = ";";

    /** The LOGGER Constant. */
    private static final Logger LOGGER = LoggerFactory.getLogger(RedirectMapReviewProcess.class);

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private MessageGatewayService messageGatewayService;

    private String senderId;
    private String receivers;

    @SuppressWarnings ("unchecked")
    @Activate
    protected final void activate(final ComponentContext context)
    {
        final Dictionary<?, ?> properties = context.getProperties();
        Object sender = properties.get("redirectmapChangeReview.sender");
        Object receiverIds = properties.get("redirectmapChangeReview.receivers");
        senderId = PropertiesUtil.toString(sender, StringUtils.EMPTY);
        receivers = PropertiesUtil.toString(receiverIds, StringUtils.EMPTY);
    }

    @Override
    public void execute(final WorkItem item, final WorkflowSession workflowSession, final MetaDataMap metaDataMap)
            throws WorkflowException
    {
        String fileName = null;
        try {
            ResourceResolver resolver = getSystemResolver();
            Session session = resolver.adaptTo(Session.class);
            Set<String> historyFileSet = getFileSet(getVersionNode(item, session));
            Set<String> authorFileSet = getFileSet(getCurrentFileNode(item, resolver));
            StringBuilder diffData = getDiffData(historyFileSet, authorFileSet);
            fileName = createDiffFile(diffData);
            if (StringUtils.isNotEmpty(fileName)) {
                sendMailNow(item.getWorkflow().getInitiator(), fileName);
            }
        } catch (LoginException e) {
            LOGGER.error(e.getMessage(), e);
        } finally {
            deleteFile(fileName);
        }
    }

    private ResourceResolver getSystemResolver() throws LoginException
    {
        Map<String, Object> paramMap = new HashMap<String, Object>();
        paramMap.put(ResourceResolverFactory.SUBSERVICE, "readWriteService");
        ResourceResolver resolver = this.resourceResolverFactory.getServiceResourceResolver(paramMap);
        return resolver;
    }

    private void deleteFile(String fileName)
    {
        if (StringUtils.isNotEmpty(fileName)) {
            File file = new File(fileName);
            if (file.delete()) {
                LOGGER.info(fileName + " deleted successfully");
            } else {
                LOGGER.error("Failed to delete the file: " + fileName);
            }
        }
    }

    private StringBuilder getDiffData(Set<String> compareRef, Set<String> newFile)
    {
        StringBuilder builder = new StringBuilder();
        Set<String> newFileTemp = new LinkedHashSet<String>();
        newFileTemp.addAll(newFile);
        if (compareRef.size() > 0) {
            for (String s : newFileTemp) {
                if (compareRef.contains(s)) {
                    compareRef.remove(s);
                    newFile.remove(s);
                }
            }
            builder.append("Previous Version exists, following is the analysis of the change:\n");
            analyseChange(compareRef, newFile, builder);
        } else {
            if (newFile.size() > 0) {
                builder.append("Previous Version could not be found, considering these as fresh mappings introduced.\n\n");
                populateBuilder(newFile, builder);
            } else {
                builder.append("File is new, doesn't contain any rule yet.");
            }
        }
        return builder;
    }

    private void analyseChange(Set<String> compareRef, Set<String> newFile, StringBuilder builder)
    {
        if (compareRef.size() == 0) {
            if (newFile.size() == 0) {
                builder.append("\nThere are no changes in new file Syntactically.");
                builder.append("\nPossible addition of duplicate Rule which might not have any impact.\n\n");
            } else {
                builder.append("\nNew rules have been added on top of existing rules as listed below: \n\n");
                populateBuilder(newFile, builder);
            }
        } else {
            if (newFile.size() == 0) {
                builder.append("\nFew rules have been deleted as notified below\n\n");
                populateBuilder(compareRef, builder);
            } else {
                builder.append("\nChanges in both newly added file and reference version file.");
                builder.append("\nSuggests New Rules being added or Previous rules might be modified or deleted.");
                builder.append("\n following is the list:\n");
                builder.append("\n\nUnmatched Rules from previous versioned file: \n");
                populateBuilder(compareRef, builder);
                builder.append("\n\nUnmatched Rules in modified file: \n");
                populateBuilder(newFile, builder);
            }
        }
    }

    private void populateBuilder(Set<String> fileSet, StringBuilder builder)
    {
        for (String s : fileSet) {
            builder.append(TAB_SPACE + s + NEW_LINE);
        }
    }

    private Node getCurrentFileNode(final WorkItem item, ResourceResolver resolver)
    {
        Node authorFileNode = null;
        Resource authorFileResource = resolver
                .getResource(item.getWorkflowData().getPayload() + "/jcr:content/redirectMap.txt/jcr:content");
        if (authorFileResource != null) {
            authorFileNode = authorFileResource.adaptTo(Node.class);
        }
        return authorFileNode;
    }

    private Node getVersionNode(final WorkItem item, Session session)
    {
        VersionManager versionManager;
        Node versionNode = null;
        try {
            versionManager = session.getWorkspace().getVersionManager();
            VersionHistory versionHistory = versionManager
                    .getVersionHistory(item.getWorkflow().getWorkflowData().getPayload().toString() +
                            "/jcr:content");
            Node workspaceNode = session.getNode(item.getWorkflow().getWorkflowData().getPayload().toString() +
                    "/jcr:content");
            String reference = null;
            if (workspaceNode.hasProperty("jcr:predecessors") && workspaceNode.getProperty("jcr:predecessors").getValues().length > 0) {
                reference = workspaceNode.getProperty("jcr:predecessors").getValues()[0].getString();
            }
            NodeIterator itr = versionHistory.getAllLinearFrozenNodes();
            Node lastVersionNode = null;
            lastVersionNode = extractActualBaseVersion(reference, itr);
            versionNode = session.getNode(lastVersionNode.getPath() + "/jcr:frozenNode/redirectMap.txt/jcr:content");
        } catch (RepositoryException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return versionNode;
    }

    private Node extractActualBaseVersion(String reference, NodeIterator itr) throws RepositoryException, ValueFormatException,
            PathNotFoundException
    {
        Node lastVersionNode = null;

        while (itr.hasNext()) {
            Node frozenNode = (Node) itr.next();
            lastVersionNode = frozenNode;
            if (frozenNode.hasProperty("jcr:uuid") && StringUtils.isNotEmpty(reference) &&
                    frozenNode.getProperty("jcr:uuid").getString().equalsIgnoreCase(reference))
            {
                break;
            }
        }
        return lastVersionNode;
    }

    private Set<String> getFileSet(Node fileNode)
    {
        Set<String> fileSet = new LinkedHashSet<String>();
        try {
            if (fileNode != null && fileNode.hasProperty(JCR_DATA)) {
                BufferedReader br = null;
                try {
                    InputStream is = fileNode.getProperty(JCR_DATA).getBinary().getStream();
                    br = new BufferedReader(new InputStreamReader(is));
                    String sCurrentLine;
                    while ((sCurrentLine = br.readLine()) != null) {
                        fileSet.add(sCurrentLine);
                    }
                } catch (IOException e) {
                    LOGGER.error(e.getMessage(), e);
                } finally {
                    closeBufferedReader(br);
                }
            }
        } catch (RepositoryException e) {
            LOGGER.error("Exception Reading File", e.getMessage(), e);
        }
        return fileSet;
    }

    private void closeBufferedReader(BufferedReader br)
    {
        try {
            if (br != null) {
                br.close();
            }
        } catch (IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
    }

    private void sendMailNow(String initiator, String fileName)
    {
        try {
            final MultiPartEmail email = new MultiPartEmail();
            email.setFrom(senderId, "RedirectMap Observator");
            if (!receivers.contains(SEMICOLON_DELIMITER)) {
                email.addTo(receivers.trim());
            } else {
                final String[] receiverArray = receivers.split(SEMICOLON_DELIMITER);
                for (final String s : receiverArray) {
                    if (!StringUtils.EMPTY.equals(s.trim())) {
                        email.addTo(s.trim());
                    }
                }
            }
            email.setSubject("Redirect Map Change Review");
            email.setMsg("Redirect Map has been modified recently by " + initiator + ", please review the change.");
            addAttachment(fileName, email);
            messageGatewayService.getGateway(MultiPartEmail.class).send(email);
        } catch (final EmailException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void addAttachment(String fileName, final MultiPartEmail email) throws EmailException
    {
        final EmailAttachment attachment = new EmailAttachment();
        attachment.setPath(fileName);
        attachment.setDisposition(EmailAttachment.ATTACHMENT);
        attachment.setDescription("Redirect Map Changes");
        attachment.setName(fileName);
        email.attach(attachment);
    }

    public String createDiffFile(StringBuilder builder)
    {
        String fileName = StringUtils.EMPTY;
        BufferedWriter bwr = null;
        try {
            Calendar calendar = new GregorianCalendar();
            fileName = "Diff_" + calendar.getTimeInMillis() + ".txt";
            bwr = new BufferedWriter(new FileWriter(new File(fileName)));
            bwr.write(builder.toString());
            bwr.flush();
            bwr.close();
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        } finally {
            if (bwr != null) {
                try {
                    bwr.close();
                } catch (final IOException e) {
                    LOGGER.error(e.getMessage(), e);
                }

            }
        }
        return fileName;
    }

}
