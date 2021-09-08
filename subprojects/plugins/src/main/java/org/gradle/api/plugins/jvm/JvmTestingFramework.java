/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins.jvm;

import org.gradle.api.Incubating;
import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.testing.Test;

import java.util.List;

/**
 * Defines a framework for running automated tests (JUnit 4/5, TestNG) which will be used by a {@link JvmTestSuiteTarget} of a {@link JvmTestSuite}.
 *
 * @since 7.3
 */
@Incubating
@NonNullApi
@SuppressWarnings("unused")
public interface JvmTestingFramework {
    Property<String> getVersion();

    List<Dependency> getCompileOnlyDependencies();
    List<Dependency> getImplementationDependencies();
    List<Dependency> getRuntimeOnlyDependencies();

    TestFramework getTestFramework(Test test);
}
