/*
 * Copyright 2016 David Murphy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.demo.kafka.workitem;


import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.demo.kafka.KafkaClient;

public class KafkaWorkItemHandler implements WorkItemHandler {
	
	private static final Logger LOG = LoggerFactory.getLogger(KafkaWorkItemHandler.class);
	

	public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
		String topic = (String)workItem.getParameter("Topic");
		String key = (String)workItem.getParameter("Key");
		String value = (String)workItem.getParameter("Value");
		
		try {
			KafkaClient client = (KafkaClient)new InitialContext().lookup("java:module/KafkaClientBean!com.redhat.demo.kafka.KafkaClient");
			client.send(topic, key, value);
		} catch (NamingException e) {
			LOG.error("Could not find Kafka client", e);
			manager.abortWorkItem(workItem.getId());
		}

	}

	public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
		// TODO Auto-generated method stub

	}

}
