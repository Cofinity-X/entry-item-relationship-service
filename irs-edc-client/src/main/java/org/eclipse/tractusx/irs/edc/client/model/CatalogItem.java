/********************************************************************************
 * Copyright (c) 2022,2024
 *       2022: ZF Friedrichshafen AG
 *       2022: ISTOS GmbH
 *       2022,2024: Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *       2022,2023: BOSCH AG
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
package org.eclipse.tractusx.irs.edc.client.model;

import java.time.Instant;

import lombok.Builder;
import lombok.Data;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.tractusx.irs.edc.client.ContractNegotiationService;

/**
 * Catalog Item as it is stored in the cache and used by {@link ContractNegotiationService}.
 */
@Builder
@Data
public class CatalogItem {
    private String assetPropId;
    private String itemId;
    private Policy policy;
    private String connectorId;
    private String offerId;
    private Instant validUntil;
}
