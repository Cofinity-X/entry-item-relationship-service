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
package org.eclipse.tractusx.ess.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.tractusx.ess.bpn.validation.BPNIncidentValidation;
import org.eclipse.tractusx.ess.irs.IrsFacade;
import org.eclipse.tractusx.irs.common.JobProcessingFinishedEvent;
import org.eclipse.tractusx.irs.component.Jobs;
import org.eclipse.tractusx.irs.component.assetadministrationshell.AssetAdministrationShellDescriptor;
import org.eclipse.tractusx.irs.component.enums.AspectType;
import org.eclipse.tractusx.irs.component.partasplanned.PartAsPlanned;
import org.eclipse.tractusx.irs.component.partsiteinformationasplanned.PartSiteInformationAsPlanned;
import org.eclipse.tractusx.irs.data.StringMapper;
import org.eclipse.tractusx.irs.edc.client.EdcSubmodelFacade;
import org.eclipse.tractusx.irs.edc.client.exceptions.EdcClientException;
import org.eclipse.tractusx.irs.edc.client.model.notification.EdcNotification;
import org.eclipse.tractusx.irs.edc.client.model.notification.EdcNotificationHeader;
import org.eclipse.tractusx.irs.registryclient.discovery.ConnectorEndpointsService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Listens for {@link JobProcessingFinishedEvent} and calling callbackUrl with notification.
 * Execution is done in a separate thread.
 */
@Slf4j
@Service
@SuppressWarnings("PMD.TooManyMethods")
class InvestigationJobProcessingEventListener {

    private final IrsFacade irsFacade;
    private final ConnectorEndpointsService connectorEndpointsService;
    private final EdcSubmodelFacade edcSubmodelFacade;
    private final BpnInvestigationJobCache bpnInvestigationJobCache;
    private final String localBpn;
    private final String localEdcEndpoint;
    private final List<String> mockRecursiveEdcAssets;
    private final EssRecursiveNotificationHandler recursiveNotificationHandler;

    /* package */ InvestigationJobProcessingEventListener(final IrsFacade irsFacade,
            final ConnectorEndpointsService connectorEndpointsService, final EdcSubmodelFacade edcSubmodelFacade,
            final BpnInvestigationJobCache bpnInvestigationJobCache, @Value("${ess.localBpn}") final String localBpn,
            @Value("${ess.localEdcEndpoint}") final String localEdcEndpoint,
            @Value("${ess.discovery.mockRecursiveEdcAsset}") final List<String> mockRecursiveEdcAssets,
            final EssRecursiveNotificationHandler recursiveNotificationHandler) {
        this.irsFacade = irsFacade;
        this.connectorEndpointsService = connectorEndpointsService;
        this.edcSubmodelFacade = edcSubmodelFacade;
        this.bpnInvestigationJobCache = bpnInvestigationJobCache;
        this.localBpn = localBpn;
        this.localEdcEndpoint = localEdcEndpoint;
        this.mockRecursiveEdcAssets = mockRecursiveEdcAssets;
        this.recursiveNotificationHandler = recursiveNotificationHandler;
    }

    private static Map<String, List<String>> getBPNsFromShells(
            final List<AssetAdministrationShellDescriptor> shellDescriptors, final String startAssetId) {
        return shellDescriptors.stream()
                               .filter(descriptor -> !descriptor.getGlobalAssetId().equals(startAssetId))
                               .collect(Collectors.groupingBy(shell -> shell.findManufacturerId().orElseThrow(),
                                       Collectors.mapping(AssetAdministrationShellDescriptor::getGlobalAssetId,
                                               Collectors.toList())));
    }

    @NotNull
    private static List<Map.Entry<String, List<String>>> getNotResolvedBPNs(
            final Map<String, List<String>> edcAddresses) {
        return edcAddresses.entrySet().stream().filter(t -> t.getValue().isEmpty()).toList();
    }

    private static PartSiteInformationAsPlanned getPartSiteInformationAsPlanned(final Jobs job)
            throws AspectTypeNotFoundException {
        final String value = getAspectTypeFromJob(job, AspectType.PART_SITE_INFORMATION_AS_PLANNED);
        return StringMapper.mapFromString(value, PartSiteInformationAsPlanned.class);
    }

    private static PartAsPlanned getPartAsPlanned(final Jobs job) throws AspectTypeNotFoundException {
        final String value = getAspectTypeFromJob(job, AspectType.PART_AS_PLANNED);
        return StringMapper.mapFromString(value, PartAsPlanned.class);
    }

    private static String getAspectTypeFromJob(final Jobs job, final AspectType aspectType)
            throws AspectTypeNotFoundException {
        log.debug("Searching for AspectType '{}'", aspectType.toString());
        return StringMapper.mapToString(job.getSubmodels()
                                           .stream()
                                           .filter(submodel -> submodel.getAspectType().endsWith(aspectType.toString()))
                                           .findFirst()
                                           .orElseThrow(() -> new AspectTypeNotFoundException(
                                                   "AspectType '%s' not found in Job.".formatted(
                                                           aspectType.toString())))
                                           .getPayload());
    }

    private static SupplyChainImpacted validatePartSiteInformationAsPlanned(final BpnInvestigationJob investigationJob,
            final Jobs completedJob) {
        SupplyChainImpacted partSiteInformationAsPlannedValidity;
        try {
            final PartSiteInformationAsPlanned partSiteInformation = getPartSiteInformationAsPlanned(completedJob);
            partSiteInformationAsPlannedValidity = BPNIncidentValidation.jobContainsIncidentBPNSs(partSiteInformation,
                    investigationJob.getIncidentBpns());
        } catch (AspectTypeNotFoundException e) {
            log.warn("Aspect not found.", e);
            partSiteInformationAsPlannedValidity = SupplyChainImpacted.UNKNOWN;
        }
        return partSiteInformationAsPlannedValidity;
    }

    private static SupplyChainImpacted validatePartAsPlanned(final Jobs completedJob) {
        SupplyChainImpacted partAsPlannedValidity;
        try {
            final PartAsPlanned partAsPlanned = getPartAsPlanned(completedJob);
            partAsPlannedValidity = BPNIncidentValidation.partAsPlannedValidity(partAsPlanned);
        } catch (AspectTypeNotFoundException e) {
            log.warn("Aspect not found.", e);
            partAsPlannedValidity = SupplyChainImpacted.UNKNOWN;
        }
        return partAsPlannedValidity;
    }

    @Async
    @EventListener
    public void handleJobProcessingFinishedEvent(final JobProcessingFinishedEvent jobProcessingFinishedEvent) {
        final UUID completedJobId = UUID.fromString(jobProcessingFinishedEvent.jobId());
        final Optional<BpnInvestigationJob> bpnInvestigationJob = bpnInvestigationJobCache.findByJobId(completedJobId);

        bpnInvestigationJob.ifPresent(investigationJob -> {
            log.info("Job is completed. Starting SupplyChainImpacted processing for job {}.", completedJobId);

            final Jobs completedJob = irsFacade.getIrsJob(completedJobId.toString());
            final SupplyChainImpacted partAsPlannedValidity = validatePartAsPlanned(completedJob);
            log.info("Local validation of PartAsPlanned Validity was done for job {}. with result {}.", completedJobId,
                    partAsPlannedValidity);
            final SupplyChainImpacted partSiteInformationAsPlannedValidity = validatePartSiteInformationAsPlanned(
                    investigationJob, completedJob);
            log.info("Local validation of PartSiteInformationAsPlanned Validity was done for job {}. with result {}.",
                    completedJobId, partSiteInformationAsPlannedValidity);

            final SupplyChainImpacted supplyChainImpacted = partAsPlannedValidity.or(
                    partSiteInformationAsPlannedValidity);
            log.debug("Supply Chain Validity result of {} and {} resulted in {}", partAsPlannedValidity,
                    partSiteInformationAsPlannedValidity, supplyChainImpacted);

            final BpnInvestigationJob investigationJobUpdate = investigationJob.update(completedJob,
                    supplyChainImpacted);

            if (supplyChainIsNotImpacted(supplyChainImpacted)) {
                triggerInvestigationOnNextLevel(completedJob, investigationJobUpdate);
                bpnInvestigationJobCache.store(completedJobId, investigationJobUpdate);
            } else {
                bpnInvestigationJobCache.store(completedJobId, investigationJobUpdate);
                recursiveNotificationHandler.handleNotification(investigationJob.getJobSnapshot().getJob().getId(),
                        supplyChainImpacted);
            }
        });
    }

    private void triggerInvestigationOnNextLevel(final Jobs completedJob,
            final BpnInvestigationJob investigationJobUpdate) {
        // Map<BPN, List<GlobalAssetID>>
        final Map<String, List<String>> bpns = getBPNsFromShells(completedJob.getShells(),
                completedJob.getJob().getGlobalAssetId().getGlobalAssetId());
        final HashMap<String, List<String>> resolvedBPNs = new HashMap<>();
        bpns.keySet().forEach(bpn -> resolvedBPNs.put(bpn, connectorEndpointsService.fetchConnectorEndpoints(bpn)));
        log.debug("Found Endpoints to BPNs '{}'", resolvedBPNs);

        if (thereIsUnresolvableEdcAddress(resolvedBPNs)) {
            final List<String> unresolvedBPNs = getNotResolvedBPNs(resolvedBPNs).stream()
                                                                                .map(Map.Entry::getKey)
                                                                                .toList();
            log.debug("BPNs '{}' could not be resolved to an EDC address using DiscoveryService.", unresolvedBPNs);
            log.info(
                    "Some EDC addresses could not be resolved with DiscoveryService. Updating SupplyChainImpacted to {}",
                    SupplyChainImpacted.UNKNOWN);
            investigationJobUpdate.update(completedJob, SupplyChainImpacted.UNKNOWN);
        } else {
            sendNotifications(completedJob, investigationJobUpdate, bpns);
        }
    }

    private void sendNotifications(final Jobs completedJob, final BpnInvestigationJob investigationJobUpdate,
            final Map<String, List<String>> bpns) {
        bpns.forEach((bpn, globalAssetIds) -> {
            final List<String> edcBaseUrl = connectorEndpointsService.fetchConnectorEndpoints(bpn);
            if (edcBaseUrl.isEmpty()) {
                log.warn("No EDC URL found for BPN '{}'. Setting investigation result to '{}'", bpn,
                        SupplyChainImpacted.UNKNOWN);
                investigationJobUpdate.update(completedJob, SupplyChainImpacted.UNKNOWN);
            }
            edcBaseUrl.forEach(url -> {
                try {
                    final String notificationId = sendEdcNotification(bpn, url,
                            investigationJobUpdate.getIncidentBpns(), globalAssetIds);
                    investigationJobUpdate.withNotifications(Collections.singletonList(notificationId));
                } catch (final EdcClientException e) {
                    log.error("Exception during sending EDC notification.", e);
                    investigationJobUpdate.update(completedJob, SupplyChainImpacted.UNKNOWN);
                }
            });
        });
    }

    private String sendEdcNotification(final String bpn, final String url, final List<String> incidentBpns,
            final List<String> globalAssetIds) throws EdcClientException {
        final String notificationId = UUID.randomUUID().toString();

        final boolean isRecursiveAsset = mockRecursiveEdcAssets.contains(bpn);
        final EdcNotification notification = edcRequest(notificationId, bpn, incidentBpns, globalAssetIds);
        log.debug("Sending Notification '{}'", notification);
        final var response = edcSubmodelFacade.sendNotification(url,
                isRecursiveAsset ? "notify-request-asset-recursive" : "notify-request-asset", notification);
        if (response.deliveredSuccessfully()) {
            log.info("Successfully sent notification with id '{}' to EDC endpoint '{}'.", notificationId, url);
        } else {
            throw new EdcClientException("EDC Provider did not accept message with notificationId " + notificationId);
        }

        return notificationId;
    }

    private boolean thereIsUnresolvableEdcAddress(final Map<String, List<String>> edcAddresses) {
        return !getNotResolvedBPNs(edcAddresses).isEmpty();
    }

    private EdcNotification edcRequest(final String notificationId, final String recipientBpn,
            final List<String> incidentBpns, final List<String> globalAssetIds) {
        final var header = EdcNotificationHeader.builder()
                                                .notificationId(notificationId)
                                                .recipientBpn(recipientBpn)
                                                .senderBpn(localBpn)
                                                .senderEdc(localEdcEndpoint)
                                                .replyAssetId("ess-response-asset")
                                                .replyAssetSubPath("")
                                                .notificationType("ess-supplier-request")
                                                .build();
        final var content = Map.of("incidentBpn", incidentBpns.get(0), "concernedCatenaXIds", globalAssetIds);

        return EdcNotification.builder().header(header).content(content).build();
    }

    private boolean supplyChainIsNotImpacted(final SupplyChainImpacted supplyChain) {
        return SupplyChainImpacted.NO.equals(supplyChain);
    }

}
