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

import org.gradle.api.execution.incremental.IncrementalInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.internal.Cast;
import org.gradle.internal.change.CollectingChangeVisitor;
import org.gradle.internal.execution.history.changes.InputFileChanges;

import java.util.Map;

public class DefaultIncrementalInputs implements IncrementalInputs {

    private final InputFileChanges changes;
    private final Map<Object, String> propertyNameByValue;

    public DefaultIncrementalInputs(InputFileChanges changes, Map<Object, String> propertyNameByValue) {
        this.changes = changes;
        this.propertyNameByValue = propertyNameByValue;
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public Iterable<InputFileDetails> getChanges(Object property) {
        String propertyName = determinePropertyName(property, propertyNameByValue);
        CollectingChangeVisitor visitor = new CollectingChangeVisitor();
        changes.accept(propertyName, visitor);
        return Cast.uncheckedNonnullCast(visitor.getChanges());
    }

    public static String determinePropertyName(Object property, Map<Object, String> propertyNameByValue) {
        String propertyName = propertyNameByValue.get(property);
        if (propertyName == null) {
            throw new UnsupportedOperationException("Cannot query incremental changes: No property found for " + property + ".");
        }
        return propertyName;
    }
}
