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

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

/**
 * Custom Log4j appender to send auditing events to AMQ. An audit event is sent when: - a node is executing (log message
 * contains "changed state to EXECUTING") - a node is executed (log message contains "changed state to EXECUTED") - an
 * error occurs - input ports of an executed node are provided (log message contains "has inputs from nodes: ") this
 * happens if node is exectued and also if it failed (gave error) note - these message are only available if the
 * com.knime.logging.extended plugin is enabled - relevant parameters of an executed node are provided (log message
 * contains the settings.xml) this happens if node is exectued and also if it failed (gave error) in this case which
 * parameters are send as part of audit event is controlled by interestingKeys parameters in log4j XML config file note
 * - these message are only available if the com.knime.logging.extended plugin is enabled
 *
 * @author omartinez
 */
public class AMQLogger extends AppenderSkeleton {

    /**
     * The return code for KNIME when this plugin fails to connect to the queue.
     */
    private static final int AMQ_ERROR_RETURN_CODE = 112;

    /**
     * The return code for KNIME when this plugin fails.
     */
    private static final int ERROR_RETURN_CODE = 111;

    private Connection m_connection;

    private Context m_context;

    private String m_propertiesFilePath;

    private String m_interestingKeys;

    private String m_timeZone;

    private MessageProducer m_messageProducer;

    private Session m_session;

    /**
     * Sets the Layout and starts the AMQ connection
     *
     * {@inheritDoc}
     */
    @Override
    public void activateOptions() {
        super.activateOptions();

        // Set the keys that we need to check when sending a PARAMETERS audit event
        ((CustomLogLayout)this.layout).setInterestingKeys(m_interestingKeys);
        // Set the timeZone
        ((CustomLogLayout)this.layout).setTimeZone(m_timeZone);

        // Starts AMQ connection
        try {
            // Read properties file as specified in log4j XML config file
            Properties properties = new Properties();
            try (FileInputStream fis = new FileInputStream(new File(m_propertiesFilePath))) {
                properties.load(fis);
            } catch (Exception exp) {
                exp.printStackTrace();
                // If properties file cannot be read, then KNIME must terminate
                System.exit(ERROR_RETURN_CODE);
            }

            // Create connection using connection URL specified in properties file
            m_context = new InitialContext(properties);
            ConnectionFactory connectionFactory = (ConnectionFactory)m_context.lookup("qpidConnectionFactory");
            m_connection = connectionFactory.createConnection();
            m_connection.start();

            // Create message produce that will send message to queue specified in properties file
            m_session = m_connection.createSession(true, Session.SESSION_TRANSACTED);
            Queue queue = (Queue)m_context.lookup("amqQueue");
            m_messageProducer = m_session.createProducer(queue);

        } catch (Exception exp) {
            exp.printStackTrace();
            // If AMQ can not be reached, then KNIME must terminate
            System.exit(AMQ_ERROR_RETURN_CODE);
        }
    }

    /**
     * @return propertiesFilePath
     */
    public String getPropertiesFilePath() {
        return m_propertiesFilePath;
    }

    /**
     * Set
     *
     * @param propertiesFilePath
     */
    public void setPropertiesFilePath(final String propertiesFilePath) {
        m_propertiesFilePath = propertiesFilePath;
    }

    /**
     * @return interestingKeys
     */
    public String getInterestingKeys() {
        return m_interestingKeys;
    }

    /**
     * @param interestingKeys
     */
    public void setInterestingKeys(final String interestingKeys) {
        m_interestingKeys = interestingKeys;
    }

    /**
     * @return m_timeZone
     */
    public String getTimeZone() {
        return m_timeZone;
    }

    /**
     * @param timeZone
     */
    public void setTimeZone(final String timeZone) {
        m_timeZone = timeZone;
    }

    /**
     * Close connection to AMQ {@inheritDoc}
     */
    @Override
    public void close() {
        try {
            m_connection.close();
            m_context.close();
        } catch (Exception exp) {
            exp.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean requiresLayout() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void append(final LoggingEvent event) {
        try {
            // Formats messages (it filters message so that only relevant audit events are send
            // (read Class description above)
            String formattedMessage = this.layout.format(event);
            if (formattedMessage != null) {
                TextMessage message = m_session.createTextMessage(formattedMessage);
                m_messageProducer.send(message);
                m_session.commit();
            }
        } catch (Exception exp) {
            exp.printStackTrace();
            // If AMQ can not be reached, then KNIME must terminate
            System.exit(ERROR_RETURN_CODE);
        }
    }

}
