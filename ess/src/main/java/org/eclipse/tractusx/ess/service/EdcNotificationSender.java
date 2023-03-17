/********************************************************************************
 * Copyright (c) 2021,2022
 *       2022: Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *       2022: ZF Friedrichshafen AG
 *       2022: ISTOS GmbH
 * Copyright (c) 2021,2022 Contributors to the Eclipse Foundation
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
package org.eclipse.tractusx.ess.service;

import java.util.Map;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.tractusx.edc.EdcSubmodelFacade;
import org.eclipse.tractusx.edc.exceptions.EdcClientException;
import org.eclipse.tractusx.edc.model.notification.EdcNotification;
import org.eclipse.tractusx.edc.model.notification.EdcNotificationHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EdcNotificationSender {

    private final EdcSubmodelFacade edcSubmodelFacade;
    private final String localBpn;
    private final String essUrl;

    public EdcNotificationSender(final EdcSubmodelFacade edcSubmodelFacade,
            @Value("${ess.localBpn}") final String localBpn,
            @Value("${ess.irs.url}") final String essUrl) {
        this.edcSubmodelFacade = edcSubmodelFacade;
        this.localBpn = localBpn;
        this.essUrl = essUrl;
    }

    public void sendEdcNotification(EdcNotification originalEdcNotification, SupplyChainImpacted supplyChainImpacted) {
        try {
            final String notificationId = UUID.randomUUID().toString();
            final String originalNotificationId = originalEdcNotification.getHeader().getNotificationId();
            final String recipientBpn = originalEdcNotification.getHeader().getSenderBpn();
            final String recipientUrl = originalEdcNotification.getHeader().getSenderEdc();
            final Map<String, Object> notificationContent = Map.of("result", supplyChainImpacted.getDescription());

            final EdcNotification edcRequest = edcRequest(notificationId, originalNotificationId, essUrl, localBpn,
                    recipientBpn, notificationContent);

            final var response = edcSubmodelFacade.sendNotification(recipientUrl, "ess-response-asset", edcRequest);
            if (!response.deliveredSuccessfully()) {
                throw new EdcClientException("EDC Provider did not accept message with notificationId " + notificationId);
            }
            log.info("Successfully sent response notification.");
        } catch (EdcClientException exception) {
            log.error("Cannot send edc notification", exception);
        }
    }

    private EdcNotification edcRequest(final String notificationId, final String originalId, final String senderEdc,
            final String senderBpn, final String recipientBpn, final Map<String, Object> content) {
        final EdcNotificationHeader header = EdcNotificationHeader.builder()
                                                                  .notificationId(notificationId)
                                                                  .senderEdc(senderEdc)
                                                                  .senderBpn(senderBpn)
                                                                  .recipientBpn(recipientBpn)
                                                                  .originalNotificationId(originalId)
                                                                  .replyAssetId("")
                                                                  .replyAssetSubPath("")
                                                                  .notificationType("ess-supplier-response")
                                                                  .build();

        return EdcNotification.builder().header(header).content(content).build();
    }

}
