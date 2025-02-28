/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.internal.tasks.properties.ContentTracking;
import org.gradle.api.internal.tasks.properties.OutputFilePropertyType;
import org.gradle.api.tasks.TaskFilePropertyBuilder;
import org.gradle.api.tasks.TaskOutputFilePropertyBuilder;

public class DefaultTaskOutputFilePropertyRegistration extends AbstractTaskFilePropertyRegistration implements TaskOutputFilePropertyRegistration {
    private final OutputFilePropertyType outputFilePropertyType;

    public DefaultTaskOutputFilePropertyRegistration(StaticValue value, OutputFilePropertyType outputFilePropertyType) {
        super(value);
        this.outputFilePropertyType = outputFilePropertyType;
    }

    @Override
    public TaskOutputFilePropertyBuilder withPropertyName(String propertyName) {
        setPropertyName(propertyName);
        return this;
    }

    @Override
    public TaskFilePropertyBuilder untracked() {
        return tracked(false);
    }

    @Override
    public TaskFilePropertyBuilder tracked(boolean tracked) {
        setContentTracking(tracked ? ContentTracking.TRACKED : ContentTracking.UNTRACKED);
        return this;
    }

    @Override
    public TaskOutputFilePropertyBuilder optional() {
        return optional(true);
    }

    @Override
    public TaskOutputFilePropertyBuilder optional(boolean optional) {
        setOptional(optional);
        return this;
    }

    @Override
    public OutputFilePropertyType getPropertyType() {
        return outputFilePropertyType;
    }

    @Override
    public String toString() {
        return getPropertyName() + " (Output)";
    }
}
