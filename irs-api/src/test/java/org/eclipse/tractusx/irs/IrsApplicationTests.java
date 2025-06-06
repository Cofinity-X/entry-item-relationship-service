/********************************************************************************
 * Copyright (c) 2022,2024
 *       2022: ZF Friedrichshafen AG
 *       2022: ISTOS GmbH
 *       2022,2024: Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *       2022,2023: BOSCH AG
 * Copyright (c) 2021,2025 Contributors to the Eclipse Foundation
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
package org.eclipse.tractusx.irs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.awaitility.Awaitility;
import org.eclipse.tractusx.irs.aaswrapper.job.AASTransferProcess;
import org.eclipse.tractusx.irs.aaswrapper.job.ItemDataRequest;
import org.eclipse.tractusx.irs.common.persistence.BlobPersistence;
import org.eclipse.tractusx.irs.component.JobParameter;
import org.eclipse.tractusx.irs.component.PartChainIdentificationKey;
import org.eclipse.tractusx.irs.component.enums.BomLifecycle;
import org.eclipse.tractusx.irs.component.enums.Direction;
import org.eclipse.tractusx.irs.component.enums.JobState;
import org.eclipse.tractusx.irs.connector.job.JobInitiateResponse;
import org.eclipse.tractusx.irs.connector.job.JobOrchestrator;
import org.eclipse.tractusx.irs.connector.job.JobStore;
import org.eclipse.tractusx.irs.connector.job.ResponseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = { "digitalTwinRegistry.type=central" })
@ActiveProfiles(profiles = { "local",
                             "test"
})
@Import(TestConfig.class)
class IrsApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JobStore jobStore;

    @Autowired
    private BlobPersistence inMemoryBlobStore;

    @Autowired
    private JobOrchestrator<ItemDataRequest, AASTransferProcess> jobOrchestrator;

    @Test
    void generatedOpenApiMatchesContract() throws Exception {

        final String generatedYaml = this.restTemplate.getForObject("http://localhost:" + port + "/api/api-docs.yaml",
                String.class);
        final InputStream definedYaml = Files.newInputStream(Path.of("../docs/src/api/irs-api.yaml"));

        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        final Map<String, Object> definedYamlMap = mapper.readerForMapOf(Object.class).readValue(definedYaml);
        final Map<String, Object> generatedYamlMap = mapper.readerForMapOf(Object.class).readValue(generatedYaml);

        try {
            assertThat(generatedYamlMap)
                    .usingRecursiveComparison()
                    .isEqualTo(definedYamlMap);
        } catch (AssertionError e) {
            // write changed API to file for easier comparison
            Files.writeString(Paths.get("../docs/src/api/irs-api.actual.yaml"), generatedYaml);
            throw new AssertionError("Please compare the generated irs-api.actual.yaml "
                    + "with irs-api.yaml to find the differences easily!", e);
        }

    }

    @Test
    void shouldStoreBlobResultWhenRunningJob() throws Exception {
        final JobParameter jobParameter = JobParameter.builder()
                                                      .depth(5)
                                                      .direction(Direction.DOWNWARD)
                                                      .bomLifecycle(BomLifecycle.AS_BUILT)
                                                      .aspects(List.of())
                                                      .build();

        final JobInitiateResponse response = jobOrchestrator.startJob(PartChainIdentificationKey.builder().build(), jobParameter, null);

        assertThat(response.getStatus()).isEqualTo(ResponseStatus.OK);

        Awaitility.await()
                  .atMost(10, TimeUnit.SECONDS)
                  .pollInterval(100, TimeUnit.MILLISECONDS)
                  .until(() -> jobStore.find(response.getJobId())
                                       .map(s -> s.getJob().getState())
                                       .map(state -> state == JobState.COMPLETED)
                                       .orElse(false));

        assertThat(inMemoryBlobStore.getBlob(response.getJobId())).isNotEmpty();
    }

}
