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

package com.redhat.demo.kafka.ext;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jbpm.services.api.ProcessService;
import org.kie.server.services.api.KieContainerInstance;
import org.kie.server.services.api.KieServerExtension;
import org.kie.server.services.api.KieServerRegistry;
import org.kie.server.services.api.SupportedTransports;
import org.kie.server.services.impl.KieServerImpl;
import org.kie.server.services.jbpm.JbpmKieServerExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.demo.kafka.KafkaClient;

/**
 * Adds capabilities to process Kafka event stream as signals to containers.
 * 
 */
public class KafkaKieServerExtension implements KieServerExtension {

	private static final Logger LOG = LoggerFactory
			.getLogger(KafkaKieServerExtension.class);

	private static final Boolean disabled = Boolean.parseBoolean(System
			.getProperty("org.kie.server.kafka.ext.disabled", "false"));

	private Future<Void> clientHandle = null;

	@Override
	public boolean isActive() {
		return disabled == false;
	}

	@Override
	public void init(KieServerImpl kieServer, KieServerRegistry registry) {
		KieServerExtension jbpmExtension = registry
				.getServerExtension(JbpmKieServerExtension.EXTENSION_NAME);
		if (jbpmExtension == null) {
			// no jbpm services, no kafka support
			LOG.warn(
					"Disabling KafkaKieServerExtension due to missing dependency: {}",
					JbpmKieServerExtension.EXTENSION_NAME);
			return;
		}

		final ProcessService processService = getProcessService(jbpmExtension);

		if(processService == null){
			// no process service, no kafka support
			LOG.warn(
					"Disabling KafkaKieServerExtension due to missing dependency: {}",
					ProcessService.class.getName());
			return;
		}
		
		KafkaClient client = null;
		try {
			client = (KafkaClient) new InitialContext().lookup("java:module/KafkaClientBean!com.redhat.demo.kafka.KafkaClient");
		} catch (NamingException e) {
			LOG.error("Could not retrieve KafkaClient EJB", e);
		}
		if (client != null) {
			clientHandle = client.startConsumer((k,v) -> processService.signalEvent(k, v, null));
		}
	}

	private ProcessService getProcessService(KieServerExtension jbpmExtension) {
		ProcessService processService = null;
		for (Object service : jbpmExtension.getServices()) {
			if (service != null && ProcessService.class.isAssignableFrom(service.getClass())) {
				processService = (ProcessService) service;
				break;
			}
		}
		return processService;
	}

	@Override
	public void destroy(KieServerImpl kieServer, KieServerRegistry registry) {
		clientHandle.cancel(true);
	}

	@Override
	public String getImplementedCapability() {
		return "Kafka";
	}

	@Override
	public List<Object> getServices() {
		return Collections.emptyList();
	}

	@Override
	public String getExtensionName() {
		return "Kafka";
	}

	@Override
	public Integer getStartOrder() {
		return 10;
	}

	@Override
	public List<Object> getAppComponents(SupportedTransports type) {
		return Collections.emptyList();
	}

	@Override
	public <T> T getAppComponents(Class<T> serviceType) {
		return null;
	}

	@Override
	public void createContainer(String id,
			KieContainerInstance kieContainerInstance,
			Map<String, Object> parameters) {
		// no-op, handled by jBPM extension
	}

	@Override
	public void disposeContainer(String id,
			KieContainerInstance kieContainerInstance,
			Map<String, Object> parameters) {
		// no-op, handled by jBPM extension
	}

}
