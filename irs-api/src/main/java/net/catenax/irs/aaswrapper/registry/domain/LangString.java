//
// Copyright (c) 2021 Copyright Holder (Catena-X Consortium)
//
// See the AUTHORS file(s) distributed with this work for additional
// information regarding authorship.
//
// See the LICENSE file(s) distributed with this work for
// additional information regarding license terms.
//
package net.catenax.irs.aaswrapper.registry.domain;

import lombok.Data;

/**
 * LangString
 */
@Data
class LangString {

    /**
     * language
     */
    private String language;
    /**
     * text
     */
    private String text;

}