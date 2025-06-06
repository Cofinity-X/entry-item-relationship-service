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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.eclipse.tractusx.irs.common.auth.IrsRoles;
import org.eclipse.tractusx.irs.component.JobHandle;
import org.eclipse.tractusx.irs.component.Jobs;
import org.eclipse.tractusx.irs.component.RegisterJob;
import org.eclipse.tractusx.irs.component.enums.JobState;
import org.eclipse.tractusx.irs.configuration.security.ApiKeyAuthentication;
import org.eclipse.tractusx.irs.configuration.security.ApiKeyAuthority;
import org.eclipse.tractusx.irs.controllers.IrsController;
import org.eclipse.tractusx.irs.registryclient.discovery.ConnectorEndpointsService;
import org.eclipse.tractusx.irs.testing.containers.MinioContainer;
import org.eclipse.tractusx.irs.util.TestMother;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.awaitility.Awaitility;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = { "digitalTwinRegistry.type=central" })
@ContextConfiguration(initializers = IrsFunctionalTest.MinioConfigInitializer.class)
@ActiveProfiles(profiles = { "local" })
class IrsFunctionalTest {
    private static final String ACCESS_KEY = "accessKey";
    private static final String SECRET_KEY = "secretKey";

    private static final MinioContainer minioContainer = new MinioContainer(
            new MinioContainer.CredentialsProvider(ACCESS_KEY, SECRET_KEY)).withReuse(true);
    @Autowired
    private IrsController controller;

    @BeforeAll
    static void startContainer() {
        minioContainer.start();
    }

    @AfterAll
    static void stopContainer() {
        minioContainer.stop();
    }

    @MockBean
    private ConnectorEndpointsService connectorEndpointsService;

    @Test
    void shouldStartJobAndRetrieveResult() {
        final RegisterJob registerJob = TestMother.registerJobWithoutDepth();
        when(connectorEndpointsService.fetchConnectorEndpoints(any())).thenReturn(
                List.of("http://localhost/discovery"));

        thereIsAuthentication();

        final JobHandle jobHandle = controller.registerJobForGlobalAssetId(registerJob);
        final Optional<Jobs> finishedJob = Awaitility.await()
                                                     .pollDelay(500, TimeUnit.MILLISECONDS)
                                                     .pollInterval(500, TimeUnit.MILLISECONDS)
                                                     .atMost(150, TimeUnit.SECONDS)
                                                     .until(getJobDetails(jobHandle),
                                                             jobs -> jobs.isPresent() && jobs.get()
                                                                                             .getJob()
                                                                                             .getState()
                                                                                             .equals(JobState.COMPLETED));

        assertThat(finishedJob).isPresent();
        assertThat(finishedJob.get().getRelationships()).isNotEmpty();
        assertThat(finishedJob.get().getShells()).isNotEmpty();
        assertThat(finishedJob.get().getTombstones()).isEmpty();
        assertThat(finishedJob.get().getSubmodels()).isEmpty();
        assertThat(finishedJob.get().getBpns()).isEmpty();
        assertThat(finishedJob.get().getJob()).isNotNull();
        assertThat(finishedJob.get().getJob().getSummary()).isNotNull();
        assertThat(finishedJob.get().getJob().getParameter()).isNotNull();
    }

    private void thereIsAuthentication() {
        final ApiKeyAuthentication jwtAuthenticationToken = new ApiKeyAuthentication(new
                ApiKeyAuthority("apiKey", List.of(new SimpleGrantedAuthority(IrsRoles.VIEW_IRS))));
        jwtAuthenticationToken.setAuthenticated(true);
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(jwtAuthenticationToken);
        SecurityContextHolder.setContext(securityContext);
    }

    @NotNull
    private Callable<Optional<Jobs>> getJobDetails(final JobHandle jobHandle) {
        return () -> {
            try {
                thereIsAuthentication();
                return Optional.ofNullable(controller.getJobById(jobHandle.getId(), true).getBody());
            } catch (Exception e) {
                return Optional.empty();
            }
        };
    }

    public static class MinioConfigInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            final String hostAddress = minioContainer.getHostAddress();
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(applicationContext,
                    "blobstore.persistence.minio.endpoint=http://" + hostAddress,
                    "blobstore.persistence.minio.accessKey=" + ACCESS_KEY,
                    "blobstore.persistence.minio.secretKey=" + SECRET_KEY,
                    "blobstore.jobs.containerName=jobs-test",
                    "blobstore.policies.containerName=policy-test");
        }
    }

}
