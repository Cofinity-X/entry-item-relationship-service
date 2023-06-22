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
package org.eclipse.tractusx.irs.edc.client;

import static java.util.stream.Collectors.toSet;
import static org.eclipse.tractusx.irs.edc.client.configuration.JsonLdConfiguration.NAMESPACE_EDC_ID;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.catalog.spi.Catalog;
import org.eclipse.edc.catalog.spi.Dataset;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.tractusx.irs.edc.client.configuration.JsonLdConfiguration;
import org.eclipse.tractusx.irs.edc.client.model.CatalogItem;
import org.springframework.stereotype.Component;

/**
 * EDC Catalog facade which handles pagination of the catalog, aggregation of contract offers
 * and transformation into {@link CatalogItem}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EDCCatalogFacade {

    private final EdcControlPlaneClient controlPlaneClient;
    private final EdcConfiguration config;

    private static CatalogItem createCatalogItem(final Catalog pageableCatalog, final Dataset dataset) {
        final int maxNumberOfOffers = 1;
        if (dataset.getOffers().size() > maxNumberOfOffers) {
            log.warn("Catalog Offer contains more than one Policy. Using the first one");
        }
        final Map.Entry<String, Policy> stringPolicyEntry = dataset.getOffers()
                                                                   .entrySet()
                                                                   .stream()
                                                                   .findFirst()
                                                                   .orElseThrow();
        final var builder = CatalogItem.builder()
                                       .itemId(dataset.getId())
                                       .offerId(stringPolicyEntry.getKey())
                                       .assetPropId(dataset.getProperty(NAMESPACE_EDC_ID).toString())
                                       .policy(stringPolicyEntry.getValue());
        if (pageableCatalog.getProperties().containsKey(JsonLdConfiguration.NAMESPACE_EDC_PARTICIPANT_ID)) {
            builder.connectorId(
                    pageableCatalog.getProperties().get(JsonLdConfiguration.NAMESPACE_EDC_PARTICIPANT_ID).toString());
        }
        return builder.build();
    }

    /**
     * Paginates though the catalog and collects all CatalogItems up to the
     * point where the requests Item is found.
     *
     * @param connectorUrl The EDC Connector from which the Catalog will be requested
     * @param target       The target assetID which will be searched for
     * @return The list of catalog Items up to the point where the target CatalogItem is included.
     */
    public List<CatalogItem> fetchCatalogItemsUntilMatch(final String connectorUrl, final String target) {
        int offset = 0;
        final int pageSize = config.getControlplane().getCatalogPageSize();

        log.info("Get catalog from EDC provider.");
        final Catalog pageableCatalog = controlPlaneClient.getCatalog(connectorUrl, offset);
        final List<Dataset> datasets = new ArrayList<>(pageableCatalog.getDatasets());

        boolean isLastPage = pageableCatalog.getDatasets().size() < pageSize;
        boolean isTheSamePage = false;
        Optional<Dataset> optionalContractOffer = findOfferIfExist(target, pageableCatalog);

        while (!isLastPage && !isTheSamePage && optionalContractOffer.isEmpty()) {
            offset += pageSize;
            final Catalog newPageableCatalog = controlPlaneClient.getCatalog(connectorUrl, offset);
            isTheSamePage = theSameCatalog(pageableCatalog, newPageableCatalog);
            isLastPage = newPageableCatalog.getDatasets().size() < pageSize;
            optionalContractOffer = findOfferIfExist(target, newPageableCatalog);

            if (!isTheSamePage) {
                datasets.addAll(newPageableCatalog.getDatasets());
            }
        }

        log.info("Search for offer for asset id: {}", target);
        return datasets.stream().map(dataset -> createCatalogItem(pageableCatalog, dataset)).toList();
    }

    private Optional<Dataset> findOfferIfExist(final String target, final Catalog catalog) {
        return catalog.getDatasets()
                      .stream()
                      .filter(dataset -> dataset.getProperty(NAMESPACE_EDC_ID).toString().equals(target))
                      .findFirst();
    }

    private boolean theSameCatalog(final Catalog pageableCatalog, final Catalog newPageableCatalog) {
        final Set<String> previousOffers = pageableCatalog.getDatasets()
                                                          .stream()
                                                          .map(dataset -> dataset.getProperty(NAMESPACE_EDC_ID)
                                                                                 .toString())
                                                          .collect(toSet());
        final Set<String> nextOffers = newPageableCatalog.getDatasets()
                                                         .stream()
                                                         .map(dataset -> dataset.getProperty(NAMESPACE_EDC_ID)
                                                                                .toString())
                                                         .collect(toSet());
        return previousOffers.equals(nextOffers);
    }
}
