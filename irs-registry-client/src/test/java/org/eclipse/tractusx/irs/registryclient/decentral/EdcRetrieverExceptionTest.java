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
package org.eclipse.tractusx.irs.registryclient.decentral;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EdcRetrieverExceptionTest {

    @Test
    void testBuildEdcRetrieverException() {

        final String causeMessage = "my illegal arg";
        final IllegalArgumentException cause = new IllegalArgumentException(causeMessage);
        final String expectedUrl = "my url";
        final String expectedBpn = "my bpn";

        final EdcRetrieverException builtException = new EdcRetrieverException.Builder(cause).withEdcUrl(expectedUrl)
                                                                                             .withBpn(expectedBpn)
                                                                                             .build();

        assertThat(builtException.getBpn()).isEqualTo(expectedBpn);
        assertThat(builtException.getEdcUrl()).isEqualTo(expectedUrl);
        assertThat(builtException).hasMessageContaining(causeMessage);

    }
}