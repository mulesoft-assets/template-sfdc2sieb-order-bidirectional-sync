/**
 * Mule Anypoint Template
 * Copyright (c) MuleSoft, Inc.
 * All rights reserved.  http://www.mulesoft.com
 */

package org.mule.templates.integration;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleException;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;

import com.mulesoft.module.batch.BatchTestHelper;
import com.sforce.soap.partner.SaveResult;

/**
 * The objective of this class is validating the correct behavior of the flows
 * for this Mule Anypoint Template
 */
@SuppressWarnings("unchecked")
public class BusinessLogicTestCreateOrderIT extends AbstractTemplateTestCase {

    private static final String SALESFORCE_INBOUND_FLOW_NAME = "fromSalesforceToSiebelFlow";
    private static final String SIEBEL_INBOUND_FLOW_NAME = "fromSiebelToSalesforceFlow";
    private static final int TIMEOUT_MILLIS = 180;
    private static final Logger LOGGER = LogManager.getLogger(BusinessLogicTestCreateOrderIT.class);

	public static final String SF_ACCOUNT_ID = "0012000001OH8JUAA1";
	public static final String SF_CONTRACT_ID = "80020000006qfIGAAY";
	public static final String SF_PRICEBOOK_ID = "01s20000001SwEQAA0";
	public static final String SF_PRICEBOOK_ENTRY_1 = "01u2000000WuI41AAF";
    
    private static List<String> ordersCreatedInSalesforce = new ArrayList<String>();
    private static List<String> ordersCreatedInSiebel = new ArrayList<String>();
    
    private BatchTestHelper batchTestHelper;
    private SubflowInterceptingChainLifecycleWrapper createOrderInSalesforceFlow;
    private SubflowInterceptingChainLifecycleWrapper createOrderInSiebelFlow;
    private SubflowInterceptingChainLifecycleWrapper createOrderItemInSalesforceFlow;
    private SubflowInterceptingChainLifecycleWrapper createOrderItemInSiebelFlow;
    private SubflowInterceptingChainLifecycleWrapper queryOrderInSalesforceFlow;
    private SubflowInterceptingChainLifecycleWrapper queryOrderInSiebelFlow;
    private SubflowInterceptingChainLifecycleWrapper queryOrderItemInSiebelFlow;
    private SubflowInterceptingChainLifecycleWrapper deleteOrderInSalesforceFlow;
    private SubflowInterceptingChainLifecycleWrapper deleteOrderInSiebelFlow;

    private SubflowInterceptingChainLifecycleWrapper queryOrderInSalesforceFlowById;

    @BeforeClass
    public static void beforeTestClass() {
        // Set polling frequency to 10 seconds
        System.setProperty("poll.frequency", "10000");
        System.setProperty("mule.test.timeoutSecs", "180");
        
		DateTime now = new DateTime(DateTimeZone.UTC);
		DateTimeFormatter dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		System.setProperty("watermark.default.expression.sfdc", now.toString(dateFormat));
    }

    @Before
    public void setUp() throws MuleException, Exception {
        stopAutomaticPollTriggering();
        getAndInitializeFlows();
        batchTestHelper = new BatchTestHelper(muleContext);
        createTestData();
    }

	@AfterClass
    public static void shutDown() {
        System.clearProperty("poll.frequency");
        System.clearProperty("watermark.default.expression.sfdc");
        System.clearProperty("watermark.default.expression.sieb");
    }

    @After
    public void tearDown() throws MuleException, Exception {
        cleanUpSandboxesByRemovingTestContacts();
    }

    private void stopAutomaticPollTriggering() throws MuleException {
        stopFlowSchedulers(SALESFORCE_INBOUND_FLOW_NAME);
        stopFlowSchedulers(SIEBEL_INBOUND_FLOW_NAME);
    }

    private void getAndInitializeFlows() throws InitialisationException {
        // Flow for creating contacts in Salesforce
    	createOrderInSalesforceFlow = getSubFlow("createOrderInSalesforceFlow");
    	createOrderInSalesforceFlow.initialise();
    	createOrderInSiebelFlow = getSubFlow("createOrderInSiebelFlow");
    	createOrderInSiebelFlow.initialise();
    	createOrderItemInSalesforceFlow = getSubFlow("createOrderItemInSalesforceFlow");
    	createOrderItemInSalesforceFlow.initialise();
    	createOrderItemInSiebelFlow = getSubFlow("createOrderItemInSiebelFlow");
    	createOrderItemInSiebelFlow.initialise();
    	queryOrderInSalesforceFlow = getSubFlow("queryOrderInSalesforceFlow");
    	queryOrderInSalesforceFlow.initialise();
    	queryOrderInSiebelFlow = getSubFlow("queryOrderInSiebelFlow");
    	queryOrderInSiebelFlow.initialise();
    	queryOrderItemInSiebelFlow = getSubFlow("queryOrderItemInSiebelFlow");
    	queryOrderItemInSiebelFlow.initialise();
    	deleteOrderInSalesforceFlow = getSubFlow("deleteOrderInSalesforceFlow");
    	deleteOrderInSalesforceFlow.initialise();
    	deleteOrderInSiebelFlow = getSubFlow("deleteOrderInSiebelFlow");
    	deleteOrderInSiebelFlow.initialise();
    	queryOrderInSalesforceFlowById = getSubFlow("queryOrderInSalesforceFlowById");
    	queryOrderInSalesforceFlowById.initialise();
    }

    private void executeWaitAndAssertBatchJob(String flowConstructName)
            throws Exception {

        // Execute synchronization
        runSchedulersOnce(flowConstructName);

        // Wait for the batch job execution to finish
        batchTestHelper.awaitJobTermination(TIMEOUT_MILLIS * 1000, 500);
        batchTestHelper.assertJobWasSuccessful();
    }
    
    private void cleanUpSandboxesByRemovingTestContacts()
            throws MuleException, Exception {

        final List<String> idList = new ArrayList<String>();

        for (String item : ordersCreatedInSalesforce) {
            idList.add(item);
        }

        deleteOrderInSalesforceFlow.process(getTestEvent(idList, MessageExchangePattern.REQUEST_RESPONSE));
        idList.clear();

        for (String item : ordersCreatedInSiebel) {
            deleteOrderInSiebelFlow.process(getTestEvent(item, MessageExchangePattern.REQUEST_RESPONSE));
        }

    }

    private void createTestData() throws MuleException, Exception {
        Map<String, Object> sfOrder = new HashMap<String, Object>();
        sfOrder.put("EffectiveDate", new Date());  
        sfOrder.put("Status", "Draft");  
		sfOrder.put("ContractId", SF_CONTRACT_ID); 
		sfOrder.put("Pricebook2Id", SF_PRICEBOOK_ID); 
		sfOrder.put("AccountId", SF_ACCOUNT_ID);
		sfOrder.put("Description", "sfdc2sieb-order-bidirectional " + new Date(System.currentTimeMillis()));
        
		SaveResult res = (SaveResult) createOrderInSalesforceFlow.process(getTestEvent(sfOrder)).getMessage().getPayload();
		sfOrder.put("Id", res.getId());
		ordersCreatedInSalesforce.add(res.getId());
		LOGGER.info("Order saved " + res.getId());
		
		Map<String, Object> sfOrderItem = new HashMap<String, Object>();
		sfOrderItem.put("OrderId", sfOrder.get("Id"));
		sfOrderItem.put("Quantity", "1");
		sfOrderItem.put("UnitPrice", "100");
		sfOrderItem.put("PricebookEntryId", SF_PRICEBOOK_ENTRY_1);
		SaveResult res1 = (SaveResult) createOrderItemInSalesforceFlow.process(getTestEvent(sfOrderItem)).getMessage().getPayload();
		sfOrderItem.put("Id", res1.getId());

		LOGGER.info("Order item saved " + res1.getId());
    }
    
    @Test
    public void testMainFlow() throws MuleException, Exception {
    	
        // Execution
        executeWaitAndAssertBatchJob(SALESFORCE_INBOUND_FLOW_NAME);

        Map<String, Object> query1 = new HashMap<String, Object>();
        query1.put("Id", ordersCreatedInSalesforce.get(0));
        HashMap resp1 = (HashMap) queryOrderInSalesforceFlowById.process(getTestEvent(query1)).getMessage().getPayload();
        LOGGER.info(resp1);

        Map<String, Object> query2 = new HashMap<String, Object>();
        query2.put("OrderNumber", resp1.get("OrderNumber"));
        ArrayList<HashMap<String, Object>> resp2 = (ArrayList<HashMap<String, Object>>) queryOrderInSiebelFlow.process(getTestEvent(query2)).getMessage().getPayload();
        LOGGER.info(resp2);
        
        Assert.assertEquals("There should be one order synced in Siebel", 1, resp2.size());
        String id = (String) resp2.get(0).get("Id");
		ordersCreatedInSiebel.add(id);
        
		Map<String, Object> query3 = new HashMap<String, Object>();
		query3.put("Id", id);
		ArrayList<HashMap<String, Object>> resp3 = (ArrayList<HashMap<String, Object>>) queryOrderItemInSiebelFlow.process(getTestEvent(query3)).getMessage().getPayload();
		LOGGER.info("order items " + resp3);
		
		// Assertions
        Assert.assertEquals("There should be one order line item synced in Siebel", 1, resp3.size());
    }

}
