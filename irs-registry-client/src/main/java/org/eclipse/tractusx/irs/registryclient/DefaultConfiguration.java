/********************************************************************************
 * Copyright (c) 2021,2022,2023
 *       2022: ZF Friedrichshafen AG
 *       2022: ISTOS GmbH
 *       2022,2023: Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *       2022,2023: BOSCH AG
 * Copyright (c) 2021,2022,2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0. *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/
package org.eclipse.tractusx.irs.registryclient;

import org.eclipse.tractusx.irs.edc.client.EdcSubmodelFacade;
import org.eclipse.tractusx.irs.registryclient.central.CentralDigitalTwinRegistryService;
import org.eclipse.tractusx.irs.registryclient.central.DigitalTwinRegistryClient;
import org.eclipse.tractusx.irs.registryclient.central.DigitalTwinRegistryClientImpl;
import org.eclipse.tractusx.irs.registryclient.decentral.DecentralDigitalTwinRegistryClient;
import org.eclipse.tractusx.irs.registryclient.decentral.DecentralDigitalTwinRegistryService;
import org.eclipse.tractusx.irs.registryclient.decentral.EndpointDataForConnectorsService;
import org.eclipse.tractusx.irs.registryclient.discovery.DiscoveryFinderClient;
import org.eclipse.tractusx.irs.registryclient.discovery.DiscoveryFinderClientImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * IRS configuration settings. Sets up the digital twin registry client.
 */
@Configuration
public class DefaultConfiguration {

    public static final String DIGITAL_TWIN_REGISTRY_REST_TEMPLATE = "digitalTwinRegistryRestTemplate";
    public static final String EDC_REST_TEMPLATE = "edcRestTemplate";
    private static final String CONFIG_PREFIX = "digitalTwinRegistryClient";
    private static final String CONFIG_FIELD_TYPE = "type";
    private static final String CONFIG_VALUE_DECENTRAL = "decentral";
    private static final String CONFIG_VALUE_CENTRAL = "central";

    @Bean
    @ConditionalOnProperty(prefix = CONFIG_PREFIX, name = CONFIG_FIELD_TYPE, havingValue = CONFIG_VALUE_CENTRAL)
    public CentralDigitalTwinRegistryService centralDigitalTwinRegistryService(final DigitalTwinRegistryClient client) {
        return new CentralDigitalTwinRegistryService(client);
    }

    @Bean
    @ConditionalOnProperty(prefix = CONFIG_PREFIX, name = CONFIG_FIELD_TYPE, havingValue = CONFIG_VALUE_CENTRAL)
    public DigitalTwinRegistryClient digitalTwinRegistryClientImpl(
            @Qualifier(DIGITAL_TWIN_REGISTRY_REST_TEMPLATE) final RestTemplate restTemplate,
            @Value("${digitalTwinRegistryClient.descriptorEndpoint:}") final String descriptorEndpoint,
            @Value("${digitalTwinRegistryClient.shellLookupEndpoint:}") final String shellLookupEndpoint) {
        return new DigitalTwinRegistryClientImpl(restTemplate, descriptorEndpoint, shellLookupEndpoint);
    }

    @Bean
    @ConditionalOnProperty(prefix = CONFIG_PREFIX, name = CONFIG_FIELD_TYPE, havingValue = CONFIG_VALUE_DECENTRAL)
    public DecentralDigitalTwinRegistryService decentralDigitalTwinRegistryService(
            final DiscoveryFinderClient discoveryFinderClient,
            final EndpointDataForConnectorsService endpointDataForConnectorsService,
            final DecentralDigitalTwinRegistryClient decentralDigitalTwinRegistryClient) {
        return new DecentralDigitalTwinRegistryService(discoveryFinderClient, endpointDataForConnectorsService,
                decentralDigitalTwinRegistryClient);
    }

    @Bean
    @ConditionalOnProperty(prefix = CONFIG_PREFIX, name = CONFIG_FIELD_TYPE, havingValue = CONFIG_VALUE_DECENTRAL)
    public DiscoveryFinderClient discoveryFinderClient(
            @Qualifier(DIGITAL_TWIN_REGISTRY_REST_TEMPLATE) final RestTemplate dtrRestTemplate,
            @Value("${digitalTwinRegistryClient.discoveryFinderUrl:}") final String finderUrl) {
        return new DiscoveryFinderClientImpl(finderUrl, dtrRestTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = CONFIG_PREFIX, name = CONFIG_FIELD_TYPE, havingValue = CONFIG_VALUE_DECENTRAL)
    public EndpointDataForConnectorsService endpointDataForConnectorsService(final EdcSubmodelFacade facade) {
        return new EndpointDataForConnectorsService(facade);
    }

    @Bean
    @ConditionalOnProperty(prefix = CONFIG_PREFIX, name = CONFIG_FIELD_TYPE, havingValue = CONFIG_VALUE_DECENTRAL)
    public DecentralDigitalTwinRegistryClient decentralDigitalTwinRegistryClient(
            @Qualifier(EDC_REST_TEMPLATE) final RestTemplate edcRestTemplate) {
        return new DecentralDigitalTwinRegistryClient(edcRestTemplate);
    }

}
