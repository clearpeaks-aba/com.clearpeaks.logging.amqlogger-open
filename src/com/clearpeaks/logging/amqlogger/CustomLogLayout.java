/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by ClearPeaks
 *  Website: https://www.clearpeaks.com/; Email: info@clearpeaks.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 * ---------------------------------------------------------------------
 *
 * History
 *   Jan 19, 2021 (omartinez): created
 */
package com.clearpeaks.logging.amqlogger;

import java.io.StringReader;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LoggingEvent;
import org.knime.core.node.NodeLogger.KNIMELogMessage;
import org.knime.core.node.workflow.NodeID;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Custom Log Layout Class to format audit Events
 *
 * @author omartinez
 */
public class CustomLogLayout extends PatternLayout {

    private static final String HAS_INPUTS_FROM_NODES = "has inputs from nodes: ";

    private static final int ACTION_TYPE_EXECUTING = 1;

    private static final String ACTION_TYPE_EXECUTING_STR = "EXECUTING";

    private static final int ACTION_TYPE_EXECUTED = 2;

    private static final String ACTION_TYPE_EXECUTED_STR = "EXECUTED";

    private static final int ACTION_TYPE_INPUTPORTS = 3;

    private static final String ACTION_TYPE_INPUTPORTS_STR = "INPUTPORTS";

    private static final int ACTION_TYPE_PARAMETERS = 4;

    private static final String ACTION_TYPE_PARAMETERS_STR = "PARAMETERS";

    private static final int ACTION_TYPE_ERROR = 5;

    private static final String ACTION_TYPE_ERROR_STR = "ERROR";

    private HashSet<String> m_interestingKeys;

    private String m_timeZone;

    private String m_hostName;

    private String m_userName;

    /**
     * {@inheritDoc}
     */
    @Override
    public void activateOptions() {
        super.activateOptions();

        // Get hostname and user name
        m_hostName = "";
        try {
            m_hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            m_hostName = "unknown";
        }
        m_userName = System.getProperty("user.name");
    }

    /**
     * Sets the keys that the node settings should be scanned for. Those items are logged.
     *
     * @param interestingKeysStr the comma separated list of keywords coming from the log4j config file.
     */
    public void setInterestingKeys(final String interestingKeysStr) {
        m_interestingKeys = new HashSet<String>();
        for (String key : interestingKeysStr.split(",")) {
            m_interestingKeys.add(key);
        }
    }

    /**
     * Sets the timeZone
     *
     * @param timeZone
     */
    public void setTimeZone(final String timeZone) {
        m_timeZone = timeZone;
    }

    /**
     * Work-around to be able to invoke private methods on the KNIMELogMessage class.
     *
     * @param instance
     * @param name name of the method to invoke
     * @param failedMessage what we need to return if calling the method failed
     *
     * @return toString representation of result of invoking method; or failedMessage if that failed
     */
    private static String privateMethod(final Object instance, final String name, final String failedMessage) {
        try {
            Method method = instance.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            Object r = method.invoke(instance);
            return r == null ? "" : r.toString();
        } catch (Exception exp) {
            exp.printStackTrace();
            return failedMessage;
        }
    }

    /**
     * Convert String to XML Document
     *
     * @param xmlString
     * @return XML document
     */
    private static Document convertStringToXMLDocument(final String xmlString) {
        //Parser that produces DOM object trees from XML content
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        //API to obtain DOM Document instance
        DocumentBuilder builder = null;
        try {
            //Create DocumentBuilder with default configuration
            builder = factory.newDocumentBuilder();

            //Parse the content to Document object
            Document doc = builder.parse(new InputSource(new StringReader(xmlString)));
            return doc;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Creates a generic audit message to be sent to the queue.
     *
     * @param message the message
     * @param actionType the type of the performed and logged action
     * @param actionTypeStr a string description of the logged action
     * @param timeStamp a string describing when the action occurred
     * @param jobId the id of the job in which the action occurred
     * @param nodeId the id of the node that performed the logged action
     * @param nodeName the name of the node that performed the logged action
     * @return a string describing the full action
     */
    private String getGenericAuditXMLMessage(final String message, final int actionType, final String actionTypeStr,
        final String timeStamp, final String jobId, final String nodeId, final String nodeName) {

        // Create and fill the XML audit message
        StringBuffer sb = new StringBuffer();

        // COMMON PART FOR ALL TYPES OF AUDIT EVENTS
        sb.append("<auditevent>");
        sb.append("    <hostname>").append(m_hostName).append("</hostname>");
        sb.append("    <username>").append(m_userName).append("</username>");
        sb.append("    <application>").append("KNIME Executor").append("</application>");
        sb.append("    <action>").append(actionTypeStr).append("</action>");
        sb.append("    <timestamp>").append(timeStamp).append("</timestamp>");
        sb.append("    <jobid>").append(jobId).append("</jobid>");
        sb.append("    <nodeid>").append(nodeId).append("</nodeid>");
        sb.append("    <nodename>").append(nodeName).append("</nodename>");

        // FOR INPUT PORTS, WE ADD PARAM WITH INFO ON INPUT PORTS
        if (actionType == ACTION_TYPE_INPUTPORTS) {
            sb.append("    <inputports>").append(message.substring(message.indexOf(HAS_INPUTS_FROM_NODES) + 23))
                .append("</inputports>");
        }

        // FOR ERRORS, WE WRITE THE ERROR MESSAGE (this may contain newlines chars)
        if (actionType == ACTION_TYPE_ERROR) {
            sb.append("    <error>").append(message).append("</error>");
        }

        // FOR PARAMETERS, WE WRITE ONE LINE FOR EACH KEY OF INTEREST (which are determined in log4j XML config file)
        if (actionType == ACTION_TYPE_PARAMETERS) {

            String[] parts = message.substring(message.indexOf("<?xml version")).split(System.lineSeparator());

            if (parts.length > 0) {
                String parameterXML = parts[0];
                Document doc = convertStringToXMLDocument(parameterXML);
                if (doc == null) {
                    sb.append("    <parameter name=\"parsingerror\">").append("XML parameters could not be parsed")
                        .append("</parameter>");
                } else {
                    NodeList entryNodes = doc.getElementsByTagName("entry");
                    for (int i = 0; i < entryNodes.getLength(); i++) {
                        Element entry = (Element)entryNodes.item(i);
                        String key = entry.getAttribute("key");
                        if (m_interestingKeys.contains(key)) {
                            sb.append("    <parameter name=\"").append(key).append("\">")
                                .append(entry.getAttribute("value")).append("</parameter>");
                        }
                    }
                }

                if (parts.length > 1) {
                    for( int i = 1; i < parts.length; i++)
                    {
                        String flowVariable = parts[i].replace("FlowVariable: ", "");
                        if (!flowVariable.contains("knime.workspace=")) { // ignore knime.workspace flow variable
                            sb.append("    <parameter name=\"flowvariable\">")
                                .append(flowVariable).append("</parameter>");
                        }
                    }
                }
            }else {
                sb.append("    <parameter name=\"parsingerror\">").append("nor XML parameters nor flow variables could be parsed")
                .append("</parameter>");
            }

        }

        // AGAIN COMMON PARTS FOR ALL EVENTS
        sb.append("</auditevent>");
        sb.append("\n");

        return sb.toString();
    }


    /**
     * Format a logging event into XML String
     *
     * {@inheritDoc}
     */
    @Override
    public String format(final LoggingEvent event) {

        // Check if message is not KNIMELogMessage, it is not then for sure we do not want to send this for auditing
        Object messageObj = event.getMessage();
        if (!(messageObj instanceof KNIMELogMessage)) {
            return null;
        }

        // Extract log message
        String message = messageObj.toString();

        // Determine action type based on message content (or if log level is ERROR, in which case we always send audit event)
        // See AMQLogger class description for more information on which audit events are sent
        int actionType = -1;
        String actionTypeStr = "";
        if (message.contains("changed state to EXECUTING")) {
            actionType = ACTION_TYPE_EXECUTING;
            actionTypeStr = ACTION_TYPE_EXECUTING_STR;
        } else if (message.contains("changed state to EXECUTED")) {
            actionType = ACTION_TYPE_EXECUTED;
            actionTypeStr = ACTION_TYPE_EXECUTED_STR;
        } else if (message.contains(HAS_INPUTS_FROM_NODES)) {
            actionType = ACTION_TYPE_INPUTPORTS;
            actionTypeStr = ACTION_TYPE_INPUTPORTS_STR;
        } else if (message.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")) {
            actionType = ACTION_TYPE_PARAMETERS;
            actionTypeStr = ACTION_TYPE_PARAMETERS_STR;
        } else if (event.getLevel().equals(Level.ERROR)) {
            actionType = ACTION_TYPE_ERROR;
            actionTypeStr = ACTION_TYPE_ERROR_STR;
        }

        // We only compile audit message message is related to either if the actions of interest
        if (actionType > 0) {

            // Convert the long timestamp to string
            Date date = new Date(event.getTimeStamp());

            // Conversion
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            sdf.setTimeZone(TimeZone.getTimeZone(m_timeZone));
            String timeStamp = sdf.format(date);

            // Cast message to KNIMELogMessage so we can access methods to get nodeId, nodeName and jobId
            KNIMELogMessage eventMessage = (KNIMELogMessage)messageObj;

            // Get nodeID
            NodeID nodeID = eventMessage.getNodeID();
            String nodeIdStr = "error reading nodeID (null)";
            if (nodeID != null) {
                nodeIdStr = nodeID.toString();
            }

            // Get jobId and nodeName
            // TODO: the getters are private, so we need a workaround to access the values
            // (a issue is opened in KNIME to fix this; once fixed this must be changed)
            String jobId = privateMethod(eventMessage, "getjobID", "error parsing jobID");
            String nodeName = privateMethod(eventMessage, "getNodeName", "error parsing nodeName");

            return getGenericAuditXMLMessage(message, actionType, actionTypeStr, timeStamp, jobId, nodeIdStr, nodeName);
        } else {
            return null;
        }
    }
}
