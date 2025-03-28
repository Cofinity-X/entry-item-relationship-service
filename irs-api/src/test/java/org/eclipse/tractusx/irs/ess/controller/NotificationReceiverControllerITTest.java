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
package org.eclipse.tractusx.irs.ess.controller;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.tractusx.irs.TestConfig;
import org.eclipse.tractusx.irs.configuration.security.AuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = { "digitalTwinRegistry.type=central" })
@ActiveProfiles(profiles = { "test", "local" })
@Import(TestConfig.class)
@ExtendWith({ MockitoExtension.class, SpringExtension.class })
public class NotificationReceiverControllerITTest {

    private final String path = "/ess/notification/receive";

    @LocalServerPort
    private int port;

    @MockBean
    private AuthenticationService authenticationService;

    @BeforeEach
    public void configureRestAssured() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    @Test
    void shouldReturnUnauthorizedStatusWhenAuthenticationIsMissing() {
        Mockito.when(authenticationService.getAuthentication(any(HttpServletRequest.class)))
               .thenThrow(new BadCredentialsException("Wrong ApiKey"));

        given().port(port)
               .contentType(ContentType.JSON)
               .body(" ")
               .post(path)
               .then()
               .statusCode(UNAUTHORIZED.value());
    }
}
