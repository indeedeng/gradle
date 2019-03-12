/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.execution.history.impl;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.Describable;
import org.gradle.api.execution.incremental.IncrementalInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.internal.Cast;
import org.gradle.internal.change.CollectingChangeVisitor;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.gradle.internal.execution.history.impl.DefaultIncrementalInputs.determinePropertyName;

public class RebuildIncrementalInputs implements IncrementalInputs {
    private static final Logger LOGGER = LoggerFactory.getLogger(RebuildIncrementalInputs.class);

    private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> currentInputs;
    private final Map<Object, String> propertyNameByValue;

    public RebuildIncrementalInputs(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> currentInputs, Map<Object, String> propertyNameByValue, Describable owner) {
        this.currentInputs = currentInputs;
        this.propertyNameByValue = propertyNameByValue;
        LOGGER.info("All input files are considered out-of-date for incremental {}.", owner.getDisplayName());
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public Iterable<InputFileDetails> getChanges(Object property) {
        CollectingChangeVisitor visitor = new CollectingChangeVisitor();
        CurrentFileCollectionFingerprint currentFileCollectionFingerprint = currentInputs.get(determinePropertyName(property, propertyNameByValue));
        currentFileCollectionFingerprint.visitChangesSince(FileCollectionFingerprint.EMPTY, "Input", true, visitor);
        return Cast.uncheckedNonnullCast(visitor.getChanges());
    }
}
