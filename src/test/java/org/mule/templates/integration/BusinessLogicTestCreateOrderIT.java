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
import org.mule.modules.siebel.api.model.response.CreateResult;
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
	
	public static final String SIEB_ACCOUNT_ID = "1-16RO7";
	public static final String SIEB_PRODUCT_ID = "1-3QG3";
    
    private static List<String> ordersCreatedInSalesforce = new ArrayList<String>();
    private static List<String> ordersCreatedInSiebel = new ArrayList<String>();
    
    private BatchTestHelper batchTestHelper;
    private SubflowInterceptingChainLifecycleWrapper createOrderInSalesforceFlow;
    private SubflowInterceptingChainLifecycleWrapper createOrderItemInSalesforceFlow;
    private SubflowInterceptingChainLifecycleWrapper queryOrderInSalesforceFlow;
    private SubflowInterceptingChainLifecycleWrapper queryOrderInSalesforceFlowById;
    private SubflowInterceptingChainLifecycleWrapper deleteObjectFromSalesforceFlow;
    
    private SubflowInterceptingChainLifecycleWrapper createOrderInSiebelFlow;
    private SubflowInterceptingChainLifecycleWrapper createOrderItemInSiebelFlow;
    private SubflowInterceptingChainLifecycleWrapper queryOrderInSiebelFlow;
    private SubflowInterceptingChainLifecycleWrapper queryOrderItemInSiebelFlow;
    private SubflowInterceptingChainLifecycleWrapper deleteOrderInSiebelFlow;

    @BeforeClass
    public static void beforeTestClass() {
        System.setProperty("poll.frequencyMillis", "10000");
        System.setProperty("poll.startDelayMillis", "500");
        System.setProperty("mule.test.timeoutSecs", "180");
        
		DateTimeFormatter dateFormatSfdc = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		System.setProperty("watermark.default.expression.sfdc", new DateTime(DateTimeZone.UTC).minusMinutes(5).toString(dateFormatSfdc));
		System.setProperty("watermark.default.expression.sieb", Long.toString(new DateTime().minusHours(10).toDate().getTime()));
    }

    @Before
    public void setUp() throws MuleException, Exception {
        stopAutomaticPollTriggering();
        getAndInitializeFlows();
        batchTestHelper = new BatchTestHelper(muleContext);
    }

	@AfterClass
    public static void shutDown() {
        System.clearProperty("poll.frequencyMillis");
        System.clearProperty("poll.startDelayMillis");
        System.clearProperty("mule.test.timeoutSecs");
        System.clearProperty("watermark.default.expression.sfdc");
        System.clearProperty("watermark.default.expression.sieb");
    }

    @After
    public void tearDown() throws MuleException, Exception {
        cleanUpSandboxes();
    }

    @Test
    public void testSalesforce2Siebel() throws MuleException, Exception {
    	createTestDataInSalesforce();
    	
        // Execution
        executeWaitAndAssertBatchJob(SALESFORCE_INBOUND_FLOW_NAME);

        Map<String, Object> sfdcOrderResponse = (Map<String, Object>) queryOrderInSalesforceFlowById.process(getTestEvent(ordersCreatedInSalesforce.get(0))).getMessage().getPayload();
        ArrayList<HashMap<String, Object>> siebOrderResponse = (ArrayList<HashMap<String, Object>>) queryOrderInSiebelFlow.process(getTestEvent(sfdcOrderResponse.get("OrderNumber"))).getMessage().getPayload();
        Assert.assertEquals("There should be one order synced in Siebel", 1, siebOrderResponse.size());
        
        String siebelOrderId = (String) siebOrderResponse.get(0).get("Id");
		ordersCreatedInSiebel.add(siebelOrderId);
        
		ArrayList<HashMap<String, Object>> resp3 = (ArrayList<HashMap<String, Object>>) queryOrderItemInSiebelFlow.process(getTestEvent(siebelOrderId)).getMessage().getPayload();
        Assert.assertEquals("There should be one order line item synced in Siebel", 1, resp3.size());
    }
    
    @Test
    public void testSiebel2Salesforce() throws MuleException, Exception {
    	createTestDataInSiebel();
    	
    	// Execution
        executeWaitAndAssertBatchJob(SIEBEL_INBOUND_FLOW_NAME);
        
        Map<String, Object> sfdcOrderResponse = (Map<String, Object>) queryOrderInSalesforceFlow.process(getTestEvent(ordersCreatedInSiebel.get(0))).getMessage().getPayload();
        Assert.assertNotNull("There should be one order synced in Salesforce", sfdcOrderResponse);
        Assert.assertEquals("There should be one order item synced in Salesforce", "1", ((Map<String,Object>) sfdcOrderResponse.get("OrderItems")).get("size"));
        
        ordersCreatedInSalesforce.add((String)sfdcOrderResponse.get("Id"));
    }
    

    private void stopAutomaticPollTriggering() throws MuleException {
        stopFlowSchedulers(SALESFORCE_INBOUND_FLOW_NAME);
        stopFlowSchedulers(SIEBEL_INBOUND_FLOW_NAME);
    }
    

    private void executeWaitAndAssertBatchJob(String flowConstructName) throws Exception {

        // Execute synchronization
        runSchedulersOnce(flowConstructName);

        // Wait for the batch job execution to finish
        batchTestHelper.awaitJobTermination(TIMEOUT_MILLIS * 1000, 500);
        batchTestHelper.assertJobWasSuccessful();
    }
    
    private void cleanUpSandboxes() throws MuleException, Exception {

        final List<String> idList = new ArrayList<String>();
        
        // delete orders from Salesforce
        if (!ordersCreatedInSalesforce.isEmpty()) {
        	for (String item : ordersCreatedInSalesforce) {
                idList.add(item);
            }
        	deleteObjectFromSalesforceFlow.process(getTestEvent(idList, MessageExchangePattern.REQUEST_RESPONSE));
        	idList.clear();
		}
        
        // delete Orders from Siebel
        for (String item : ordersCreatedInSiebel) {
            deleteOrderInSiebelFlow.process(getTestEvent(item, MessageExchangePattern.REQUEST_RESPONSE));
        }
        
        ordersCreatedInSalesforce.clear();
        ordersCreatedInSiebel.clear();
    }

    private void createTestDataInSalesforce() throws MuleException, Exception {
        Map<String, Object> sfOrder = new HashMap<String, Object>();
        sfOrder.put("EffectiveDate", new Date());  
        sfOrder.put("Status", "Draft");  
		sfOrder.put("ContractId", SF_CONTRACT_ID); 
		sfOrder.put("Pricebook2Id", SF_PRICEBOOK_ID); 
		sfOrder.put("AccountId", SF_ACCOUNT_ID);
		sfOrder.put("Description", "sfdc2sieb-order-bidirectional " + new Date(System.currentTimeMillis()));
        
		SaveResult salesforceOrderResponse = (SaveResult) createOrderInSalesforceFlow.process(getTestEvent(sfOrder)).getMessage().getPayload();
		sfOrder.put("Id", salesforceOrderResponse.getId());
		ordersCreatedInSalesforce.add(salesforceOrderResponse.getId());
		LOGGER.info("Order saved " + salesforceOrderResponse.getId());
		
		Map<String, Object> sfOrderItem = new HashMap<String, Object>();
		sfOrderItem.put("OrderId", sfOrder.get("Id"));
		sfOrderItem.put("Quantity", "1");
		sfOrderItem.put("UnitPrice", "100");
		sfOrderItem.put("PricebookEntryId", SF_PRICEBOOK_ENTRY_1);
		
		SaveResult salesforceOrderItemResponse = (SaveResult) createOrderItemInSalesforceFlow.process(getTestEvent(sfOrderItem)).getMessage().getPayload();
		sfOrderItem.put("Id", salesforceOrderItemResponse.getId());

		LOGGER.info("Order item saved " + salesforceOrderItemResponse.getId());
    }
    
    private void createTestDataInSiebel() throws MuleException, Exception {
    	// order
    	Map<String, Object> siebOrder = new HashMap<String, Object>();
        siebOrder.put("Currency Code", "USD");  
        siebOrder.put("Order Type", "Sales Order");  
        siebOrder.put("Account Id", SIEB_ACCOUNT_ID);
        
        CreateResult orderResponse = (CreateResult) createOrderInSiebelFlow.process(getTestEvent(siebOrder)).getMessage().getPayload();
		siebOrder.put("Id", orderResponse.getCreatedObjects().get(0));
		ordersCreatedInSiebel.add((String)siebOrder.get("Id"));
		LOGGER.info("Order saved " + siebOrder.get("Id"));
		
		// order item
		Map<String, Object> siebOrderItem = new HashMap<String, Object>();
		siebOrderItem.put("Order Header Id", siebOrder.get("Id"));
		siebOrderItem.put("Quantity Requested", "1");
		siebOrderItem.put("Extended Quantity", "1");
		siebOrderItem.put("Net Price", "100");
		siebOrderItem.put("Currency Code", "USD");
		siebOrderItem.put("Product Id", SIEB_PRODUCT_ID);
		
		CreateResult orderItemResponse = (CreateResult) createOrderItemInSiebelFlow.process(getTestEvent(siebOrderItem)).getMessage().getPayload();
		siebOrderItem.put("Id", orderItemResponse.getCreatedObjects().get(0));
		LOGGER.info("Order item saved " + siebOrderItem.get("Id"));
    }
    
    private void getAndInitializeFlows() throws InitialisationException {
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
    	
    	deleteObjectFromSalesforceFlow = getSubFlow("deleteObjectFromSalesforceFlow");
    	deleteObjectFromSalesforceFlow.initialise();
    	
    	deleteOrderInSiebelFlow = getSubFlow("deleteOrderInSiebelFlow");
    	deleteOrderInSiebelFlow.initialise();
    	
    	queryOrderInSalesforceFlowById = getSubFlow("queryOrderInSalesforceFlowById");
    	queryOrderInSalesforceFlowById.initialise();
    }
}
