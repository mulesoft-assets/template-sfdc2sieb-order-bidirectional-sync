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
import org.mule.modules.siebel.api.model.response.CreateResult;
import org.mule.processor.chain.InterceptingChainLifecycleWrapper;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;

import com.mulesoft.module.batch.BatchTestHelper;
import com.sforce.soap.partner.SaveResult;

/**
 * The objective of this class is validating the correct behavior of the flows
 * for this Mule Anypoint Template
 */
@SuppressWarnings("unchecked")
public class BusinessLogicTestCreateOrderIT extends AbstractTemplateTestCase {

    private static final String ANYPOINT_TEMPLATE_NAME = "sfdc2sieb-order-bidirectional-sync";
    private static final String SALESFORCE_INBOUND_FLOW_NAME = "fromSalesforceToSiebelFlow";
    private static final String SIEBEL_INBOUND_FLOW_NAME = "fromSiebelToSalesforceFlow";
    private static final int TIMEOUT_MILLIS = 60;

	public static final String SF_ACCOUNT_ID = "0012000001BJaC1";
	public static final String SF_CONTRACT_ID = "80020000005mj5d";
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

    @BeforeClass
    public static void beforeTestClass() {
        // Set polling frequency to 10 seconds
        System.setProperty("poll.frequency", "10000");
        System.setProperty("account.sync.policy", "syncAccount");
        
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
		System.err.println("Order saved " + res.getId());
		
		Map<String, Object> sfOrderItem = new HashMap<String, Object>();
		sfOrderItem.put("OrderId", sfOrder.get("Id"));
		sfOrderItem.put("Quantity", "1");
		sfOrderItem.put("UnitPrice", "100");
		sfOrderItem.put("PricebookEntryId", SF_PRICEBOOK_ENTRY_1);
		SaveResult res1 = (SaveResult) createOrderInSalesforceFlow.process(getTestEvent(sfOrderItem)).getMessage().getPayload();
		sfOrderItem.put("Id", res1.getId());
    }

	@AfterClass
    public static void shutDown() {
        System.clearProperty("poll.frequency");
        System.clearProperty("account.sync.policy");
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
    }

    private void cleanUpSandboxesByRemovingTestContacts()
            throws MuleException, Exception {

        final List<String> idList = new ArrayList<String>();

        for (String item : ordersCreatedInSalesforce) {
            idList.add(item);
        }

        deleteOrderInSalesforceFlow.process(getTestEvent(idList, MessageExchangePattern.REQUEST_RESPONSE));
        idList.clear();

        for (String contact : ordersCreatedInSiebel) {
            idList.add(contact);
        }

        deleteOrderInSiebelFlow.process(getTestEvent(idList, MessageExchangePattern.REQUEST_RESPONSE));
    }

    @Test
    public void testMainFlow()
            throws MuleException, Exception {
    	

        
        // Execution
        executeWaitAndAssertBatchJob(SIEBEL_INBOUND_FLOW_NAME);

        // Assertions
        //Assert.assertEquals("FirstName is not synchronized between systems.", siebelContact.get("First Name"), retrievedContactFromSalesforce.get("FirstName"));
    }

    private void executeWaitAndAssertBatchJob(String flowConstructName)
            throws Exception {

        // Execute synchronization
        runSchedulersOnce(flowConstructName);

        // Wait for the batch job execution to finish
        batchTestHelper.awaitJobTermination(TIMEOUT_MILLIS * 1000, 500);
        batchTestHelper.assertJobWasSuccessful();
    }


}
