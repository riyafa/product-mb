/*
*  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package org.wso2.mb.integration.common.clients;


import org.apache.log4j.Logger;
import org.wso2.mb.integration.common.clients.configurations.AndesJMSClientConfiguration;
import org.wso2.mb.integration.common.clients.configurations.AndesJMSConsumerClientConfiguration;
import org.wso2.mb.integration.common.clients.configurations.AndesJMSPublisherClientConfiguration;
import org.wso2.mb.integration.common.clients.exceptions.AndesClientException;
import org.wso2.mb.integration.common.clients.operations.utils.AndesClientOutputParser;
import org.wso2.mb.integration.common.clients.operations.utils.AndesClientUtils;

import javax.jms.JMSException;
import javax.naming.NamingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class represents the Andes Client which is used to publish/consume JMS messages. The JMS
 * publishers and consumers are created within this class with the help of a configuration file.
 * This class also provides functionality which can be used to evaluate JMS message publishers and
 * consumers.
 */
public class AndesClient {
    /**
     * The logger used to log information, warnings, errors, etc.
     */
    private static Logger log = Logger.getLogger(AndesClient.class);

    /**
     * The delay between starting publishers or consumers
     */
    private long startDelay = 0L;

    /**
     * The consumers that are started concurrently
     */
    List<AndesJMSConsumer> consumers = new ArrayList<AndesJMSConsumer>();

    /**
     * The publishers that are started concurrently
     */
    List<AndesJMSPublisher> publishers = new ArrayList<AndesJMSPublisher>();

    /**
     * Creates a single consumer or publisher based on the configuration passed
     *
     * @param config                      The configuration.
     * @param createConsumersAndProducers True if the client needs to create connections, sessions
     *                                    and respecting receivers or consumers. False otherwise.
     * @throws JMSException
     * @throws NamingException
     */
    public AndesClient(AndesJMSClientConfiguration config, boolean createConsumersAndProducers)
            throws NamingException, JMSException, AndesClientException, IOException {
        this(config, 1, createConsumersAndProducers);
    }

    /**
     * The constructor used for creating multiple consumer or publishers based on the configuration
     * passed.
     *
     * @param config                      The configuration.
     * @param numberOfThreads             The amount of publishers or consumers. This amount of
     *                                    threads will be started.
     * @param createConsumersAndProducers True if the client needs to create connections, sessions
     *                                    and respecting receivers or consumers. False otherwise.
     * @throws JMSException
     * @throws NamingException
     * @throws AndesClientException
     */
    public AndesClient(AndesJMSClientConfiguration config, int numberOfThreads,
                       boolean createConsumersAndProducers)
            throws IOException, JMSException, NamingException, AndesClientException {
        if (0 < numberOfThreads) {
            if (config instanceof AndesJMSConsumerClientConfiguration) {
                AndesClientUtils
                        .initializeReceivedMessagesPrintWriter(((AndesJMSConsumerClientConfiguration) config)
                                                                       .getFilePathToWriteReceivedMessages());
            }

            for (int i = 0; i < numberOfThreads; i++) {
                if (config instanceof AndesJMSConsumerClientConfiguration) {
                    consumers
                            .add(new AndesJMSConsumer((AndesJMSConsumerClientConfiguration) config, createConsumersAndProducers));
                } else if (config instanceof AndesJMSPublisherClientConfiguration) {
                    publishers
                            .add(new AndesJMSPublisher((AndesJMSPublisherClientConfiguration) config, createConsumersAndProducers));
                }
            }
        } else {
            throw new AndesClientException("The amount of subscribers cannot be less than 1. " +
                                           "Value entered is " + Integer.toString(numberOfThreads));
        }
    }

    /**
     * Starts up the consumer(s) or publisher(s) to consume or publish messages.
     *
     * @throws JMSException
     * @throws IOException
     */
    public void startClient() throws AndesClientException, JMSException, IOException {
        boolean isStartDelaySet = this.startDelay > 0L;
        for (AndesJMSConsumer consumer : consumers) {
            consumer.startClient();
            if (isStartDelaySet) {
                AndesClientUtils.sleepForInterval(this.startDelay);
            }
        }
        for (AndesJMSPublisher publisher : publishers) {
            publisher.startClient();
            if (isStartDelaySet) {
                AndesClientUtils.sleepForInterval(this.startDelay);
            }
        }
    }

    /**
     * Stops the client from publishing or consuming messages.
     *
     * @throws JMSException
     */
    public void stopClient() throws JMSException {
        for (AndesJMSConsumer consumer : consumers) {
            consumer.stopClient();
        }
        for (AndesJMSPublisher publisher : publishers) {
            publisher.stopClient();
        }

        log.info("TPS:" + this.getConsumerTPS() + " AverageLatency:" + this.getAverageLatency());
    }

    /**
     * Gets the received messages for all consumers in the client.
     *
     * @return The total number of messages received for all consumers.
     */
    public long getReceivedMessageCount() {
        long allReceivedMessageCount = 0L;
        for (AndesJMSConsumer consumer : consumers) {
            allReceivedMessageCount = allReceivedMessageCount + consumer.getReceivedMessageCount();
        }
        return allReceivedMessageCount;
    }

    /**
     * Gets the average transactions per second for consumer(s).
     *
     * @return The average TPS.
     */
    public double getConsumerTPS() {
        double tps = 0L;
        for (AndesJMSConsumer consumer : consumers) {
            tps = tps + consumer.getConsumerTPS();
        }
        return tps / consumers.size();
    }

    /**
     * Gets the average latency for consumer(s).
     *
     * @return The average latency.
     */
    public double getAverageLatency() {
        double averageLatency = 0L;
        for (AndesJMSConsumer consumer : consumers) {
            averageLatency = averageLatency + consumer.getAverageLatency();
        }
        return averageLatency / consumers.size();
    }

    /**
     * Gets the number of messages sent by the publisher(s).
     *
     * @return The number of messages.
     */
    public long getSentMessageCount() {
        long allSentMessageCount = 0L;
        for (AndesJMSPublisher publisher : publishers) {
            allSentMessageCount = allSentMessageCount + publisher.getSentMessageCount();
        }
        return allSentMessageCount;
    }

    /**
     * Gets the average transactions per seconds for publisher(s). Suppressing "UnusedDeclaration"
     * as the client acts as an service.
     *
     * @return the average transactions per seconds.
     */
    @SuppressWarnings("UnusedDeclaration")
    public double getPublisherTPS() {
        double tps = 0L;
        for (AndesJMSPublisher publisher : publishers) {
            tps = tps + publisher.getPublisherTPS();
        }
        return tps / publishers.size();
    }

    /**
     * Gets the duplicated messages received for a single consumer. This is not valid when is comes
     * to multiple consumers.
     *
     * @return A map of message identifiers and message content.
     * @throws IOException
     */
    public Map<Long, Integer> checkIfMessagesAreDuplicated()
            throws IOException {
        if (!consumers.isEmpty()) {
            AndesClientUtils.flushPrintWriters();
            AndesClientOutputParser andesClientOutputParser =
                    new AndesClientOutputParser(consumers.get(0).getConfig()
                                                        .getFilePathToWriteReceivedMessages());
            return andesClientOutputParser.getDuplicatedMessages();
        } else {
            return null;
        }
    }

    /**
     * Checks whether the received messages are in order for a single consumer. This is not valid
     * when is comes to multiple consumers.
     *
     * @return true if messages are in order, false otherwise.
     * @throws IOException
     */
    public boolean checkIfMessagesAreInOrder()
            throws IOException {
        if (!consumers.isEmpty()) {
            AndesClientOutputParser andesClientOutputParser =
                    new AndesClientOutputParser(consumers.get(0).getConfig()
                                                        .getFilePathToWriteReceivedMessages());
            return andesClientOutputParser.checkIfMessagesAreInOrder();
        } else {
            return false;
        }
    }

    /**
     * This method returns whether received messages are transacted for a single consumer. This is
     * not valid when is comes to multiple consumers.
     *
     * @param operationOccurredIndex Index of the operated message most of the time last message.
     * @return true if all messages are transacted, false otherwise.
     */
    public boolean transactedOperation(long operationOccurredIndex)
            throws IOException {
        if (0 < consumers.size()) {
            AndesClientOutputParser andesClientOutputParser =
                    new AndesClientOutputParser(consumers.get(0).getConfig()
                                                        .getFilePathToWriteReceivedMessages());
            return andesClientOutputParser.transactedOperations(operationOccurredIndex);
        } else {
            return false;
        }
    }

    /**
     * This method returns number of duplicate received messages for a single consumer. This is not
     * valid when is comes to multiple consumers.
     *
     * @return The duplicate message count.
     */
    public long getTotalNumberOfDuplicates()
            throws IOException {
        if (0 < consumers.size()) {
            AndesClientOutputParser andesClientOutputParser =
                    new AndesClientOutputParser(consumers.get(0).getConfig()
                                                        .getFilePathToWriteReceivedMessages());
            return andesClientOutputParser.numberDuplicatedMessages();
        } else {
            return -1L;
        }
    }

    /**
     * Sets the starting delay when starting publishers or consumers.
     *
     * @param startDelay The starting delay
     */
    public void setStartDelay(long startDelay) {
        this.startDelay = startDelay;
    }

    /**
     * Gets the all the consumers created by the client.
     * @return A {@link java.util.List} of
     *          {@link org.wso2.mb.integration.common.clients.AndesJMSConsumer}.
     */
    public List<AndesJMSConsumer> getConsumers() {
        return consumers;
    }

    /**
     * Gets the all the publisher created by the client.
     * @return A {@link java.util.List} of
     *          {@link org.wso2.mb.integration.common.clients.AndesJMSPublisher}.
     */
    public List<AndesJMSPublisher> getPublishers() {
        return publishers;
    }
}
