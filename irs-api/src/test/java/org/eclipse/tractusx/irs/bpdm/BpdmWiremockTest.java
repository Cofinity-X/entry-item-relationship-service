/********************************************************************************
 * Copyright (c) 2022,2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 * Copyright (c) 2021,2024 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0. 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/
package org.eclipse.tractusx.irs.bpdm;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.tractusx.irs.bpdm.BpdmWireMockConfig.BPDM_TEST;
import static org.eclipse.tractusx.irs.bpdm.BpdmWireMockConfig.bpdmResponse;
import static org.eclipse.tractusx.irs.testing.wiremock.WireMockConfig.responseWithStatus;
import static org.eclipse.tractusx.irs.testing.wiremock.WireMockConfig.restTemplateProxy;

import java.util.Optional;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@WireMockTest
class BpdmWiremockTest {
    private static final String PROXY_SERVER_HOST = "127.0.0.1";
    private BpdmFacade bpdmFacade;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMockRuntimeInfo) {
        final RestTemplate restTemplate = restTemplateProxy(PROXY_SERVER_HOST, wireMockRuntimeInfo.getHttpPort());

        bpdmFacade = new BpdmFacade(new BpdmClientImpl(restTemplate, BPDM_TEST));
    }

    @Test
    void shouldResolveManufacturerName() {
        // Arrange
        givenThat(get(urlPathEqualTo("/legal-entities/BPNL00000000TEST")).willReturn(
                responseWithStatus(200).withBody(bpdmResponse("BPNL00000000TEST", "TEST_BPN_DFT_1"))));

        // Act
        final Optional<String> manufacturerName = bpdmFacade.findManufacturerName("BPNL00000000TEST");

        // Assert
        assertThat(manufacturerName).isPresent().contains("TEST_BPN_DFT_1");
    }

    @Test
    void shouldReturnEmptyOnNotFound() {
        // Arrange
        givenThat(get(urlPathEqualTo("/legal-entities/BPNL00000000TEST")).willReturn(responseWithStatus(404)));

        // Act & Assert
        // TODO fix implementation to not throw HttpClientErrorException$NotFound
        assertThatExceptionOfType(HttpClientErrorException.class).isThrownBy(
                () -> bpdmFacade.findManufacturerName("BPNL00000000TEST"));
    }
}
