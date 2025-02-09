/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.config;

import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.aws.greengrass.lifecyclemanager.GreengrassService.SERVICES_NAMESPACE_TOPIC;
import static com.aws.greengrass.lifecyclemanager.GreengrassService.SERVICE_LIFECYCLE_NAMESPACE_TOPIC;
import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(GGExtension.class)
class ConfigurationReaderTest {
    // This is a generic test for configuration reader, hence declaring a namespace here
    // Deployment Config Merge is tested separately in integration test
    private static final String SKIP_MERGE_NAMESPACE_KEY = "notMerged";

    Configuration config = new Configuration(new Context());

    @TempDir
    Path tempDir;

    @AfterEach
    void afterEach() throws IOException {
        config.context.close();
    }

    @Test
    void GIVEN_tlog_with_ignored_namespace_WHEN_tlog_merged_to_config_with_condition_THEN_ignored_value_not_updated()
            throws Exception {
        // Create this topic with temp value
        config.lookup(SERVICES_NAMESPACE_TOPIC, "YellowSignal",
                SKIP_MERGE_NAMESPACE_KEY, "testTopic").withValue("Test");
        Path tlogPath = Paths.get(this.getClass().getResource("test.tlog").toURI());
        ConfigurationReader.mergeTLogInto(config, tlogPath, true,
                s -> !s.childOf(SKIP_MERGE_NAMESPACE_KEY));
        // block until all changes are merged in
        config.context.waitForPublishQueueToClear();
        // Test tlog file has value set to "TLogValue"
        assertEquals("Test", config.lookup(SERVICES_NAMESPACE_TOPIC, "YellowSignal",
                SKIP_MERGE_NAMESPACE_KEY, "testTopic").getOnce());
    }

    @Test
    void GIVEN_tlog_with_ignored_namespace_WHEN_tlog_merged_to_config_with_no_condition_THEN_all_values_updated()
            throws Exception {
        // Create this topic with temp value
        config.lookup(SERVICES_NAMESPACE_TOPIC, "YellowSignal",
                SKIP_MERGE_NAMESPACE_KEY, "testTopic")
                .withValue("Test");
        Path tlogPath = Paths.get(this.getClass().getResource("test.tlog").toURI());
        ConfigurationReader.mergeTLogInto(config, tlogPath, true, null);
        // block until all changes are merged in
        config.context.waitForPublishQueueToClear();
        assertEquals("TLogValue", config.lookup(SERVICES_NAMESPACE_TOPIC, "YellowSignal",
                SKIP_MERGE_NAMESPACE_KEY, "testTopic").getOnce());
    }

    @Test
    void GIVEN_tlog_WHEN_tlog_merged_to_config_with_forced_timestamp_THEN_topic_is_removed() throws Exception {
        // Create this topic with temp value
        String[] topicPathToRemove = {SERVICES_NAMESPACE_TOPIC, "YellowSignal",
                SERVICE_LIFECYCLE_NAMESPACE_TOPIC, "shutdown"};
        config.lookup(topicPathToRemove)
                .withNewerValue(Long.MAX_VALUE, "Test");
        assertEquals("Test", config.find(topicPathToRemove).getOnce());

        Path tlogPath = Paths.get(this.getClass().getResource("test.tlog").toURI());
        ConfigurationReader.mergeTLogInto(config, tlogPath, true, null);

        // block until all changes are merged in
        config.context.waitForPublishQueueToClear();
        assertNull(config.find(topicPathToRemove));
    }

    @Test
    void GIVEN_tlog_WHEN_tlog_merged_to_config_with_smaller_timestamp_THEN_topic_is_removed() throws Exception {
        // Create this topic with temp value
        String[] topicPathToRemove = {SERVICES_NAMESPACE_TOPIC, "YellowSignal",
                SERVICE_LIFECYCLE_NAMESPACE_TOPIC, "shutdown"};
        config.lookup(topicPathToRemove)
                .withNewerValue(1, "Test", true);
        assertEquals("Test", config.find(topicPathToRemove).getOnce());
        Path tlogPath = Paths.get(this.getClass().getResource("test.tlog").toURI());
        ConfigurationReader.mergeTLogInto(config, tlogPath, false, null);

        // block until all changes are merged in
        config.context.waitForPublishQueueToClear();
        assertNull(config.find(topicPathToRemove));
    }

    @Test
    void GIVEN_tlog_WHEN_tlog_merged_to_config_with_larger_timestamp_THEN_topic_is_not_removed() throws Exception {
        // Create this topic with temp value
        String[] topicPath = {SERVICES_NAMESPACE_TOPIC, "YellowSignal",
                SERVICE_LIFECYCLE_NAMESPACE_TOPIC, "shutdown"};
        config.lookup(topicPath)
                .withNewerValue(Long.MAX_VALUE, "Test");
        Path tlogPath = Paths.get(this.getClass().getResource("test.tlog").toURI());
        ConfigurationReader.mergeTLogInto(config, tlogPath, false, null);

        // block until all changes are merged in
        config.context.waitForPublishQueueToClear();
        Topic resultTopic = config.find(topicPath);
        assertEquals("Test", resultTopic.getOnce());
        assertEquals(Long.MAX_VALUE, resultTopic.getModtime());
    }

    @Test
    void GIVEN_tlog_merge_WHEN_container_node_removed_in_tlog_THEN_node_is_removed() throws Exception {
        // Create this topic with temp value
        String[] topicPath = {SERVICES_NAMESPACE_TOPIC, "YellowSignal",
                SERVICE_LIFECYCLE_NAMESPACE_TOPIC, "shutdown"};
        config.lookup(ArrayUtils.add(topicPath, "script"))
                .withNewerValue(1, "Test");
        Path tlogPath = Paths.get(this.getClass().getResource("test.tlog").toURI());
        ConfigurationReader.mergeTLogInto(config, tlogPath, false, null);

        // block until all changes are merged in
        config.context.waitForPublishQueueToClear();
        assertNull(config.findNode(topicPath));
    }

    @Test
    void GIVEN_tlog_merge_WHEN_container_node_removed_at_smaller_timestamp_THEN_node_is_not_removed() throws Exception {
        // Create this topic with temp value
        String[] topicPath = {SERVICES_NAMESPACE_TOPIC, "YellowSignal",
                SERVICE_LIFECYCLE_NAMESPACE_TOPIC, "shutdown"};
        config.lookup(ArrayUtils.add(topicPath, "script"))
                .withNewerValue(Long.MAX_VALUE, "Test");
        config.context.waitForPublishQueueToClear();
        assertEquals(Long.MAX_VALUE, config.findNode(topicPath).modtime);
        Path tlogPath = Paths.get(this.getClass().getResource("test.tlog").toURI());
        ConfigurationReader.mergeTLogInto(config, tlogPath, false, null);

        // block until all changes are merged in
        config.context.waitForPublishQueueToClear();
        assertNotNull(config.findNode(topicPath));
        assertEquals(Long.MAX_VALUE, config.findNode(topicPath).modtime);
    }

    @Test
    void GIVEN_tlog_WHEN_merge_THEN_first_and_last_line_is_merged() throws Exception {
        Path tlogPath = Paths.get(this.getClass().getResource("test.tlog").toURI());
        ConfigurationReader.mergeTLogInto(config, tlogPath, false, null);

        // block until all changes are merged in
        config.context.waitForPublishQueueToClear();

        assertEquals("firstline", config.find("test", "firstline").getOnce());
        assertEquals("lastline", config.find("test", "lastline").getOnce());
    }

    @Test
    void GIVEN_tlog_WHEN_merge_THEN_removed_topics_is_not_present() throws Exception {
        Path tlogPath = Paths.get(this.getClass().getResource("removeTopics.tlog").toURI());
        ConfigurationReader.mergeTLogInto(config, tlogPath, false, null);

        // block until all changes are merged in
        config.context.waitForPublishQueueToClear();

        assertEquals(0, config.findTopics("first", "second").children.size());
    }

    @Test
    void GIVEN_tlog_WHEN_merge_THEN_topics_created_with_correct_modtime() throws Exception {
        Path tlogPath = Paths.get(this.getClass().getResource("createTopics.tlog").toURI());
        ConfigurationReader.mergeTLogInto(config, tlogPath, false, null);

        // block until all changes are merged in
        config.context.waitForPublishQueueToClear();

        // all parent topics should have modtime as the TS from the tlog
        assertEquals(5, config.findTopics("first").modtime);
        assertEquals(5, config.findTopics("first", "second").modtime);
        assertEquals(5, config.findTopics("first", "second", "third").modtime);
        assertEquals(5, config.find("first", "second", "third", "DeploymentId").modtime);

        tlogPath = Paths.get(this.getClass().getResource("updateTopics.tlog").toURI());
        ConfigurationReader.mergeTLogInto(config, tlogPath, false, null);

        // block until all changes are merged in
        config.context.waitForPublishQueueToClear();

        // all parent topics modtime should be updated to TS from tlog
        assertEquals(6, config.findTopics("first").modtime);
        assertEquals(6, config.findTopics("first", "second").modtime);
        assertEquals(6, config.findTopics("first", "second").modtime);
        assertEquals(6, config.findTopics("first", "second", "newChild").modtime);
    }

    @Test
    void GIVEN_corrupted_tlog_WHEN_validate_tlog_THEN_return_false(ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, MalformedInputException.class);
        Path emptyTlogPath = Files.createTempFile(tempDir, null, null);
        assertFalse(ConfigurationReader.validateTlog(emptyTlogPath));

        // test a config file with non-UTF8 encoding
        Path corruptedTlogPath = Paths.get(this.getClass().getResource("corruptedConfig.tlog").toURI());
        assertFalse(ConfigurationReader.validateTlog(corruptedTlogPath));
    }
}
