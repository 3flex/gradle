/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.internal.tasks.WorkNodeAction;
import org.gradle.internal.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;

/**
 * A single transformation step.
 *
 * Transforms a subject by invoking a transformer on each of the subjects files.
 */
public class TransformationStep implements Transformation, TaskDependencyContainer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransformationStep.class);
    public static final Equivalence<? super TransformationStep> FOR_SCHEDULING = Equivalence.identity();

    private final Transformer transformer;
    private final TransformerInvoker transformerInvoker;
    private final DomainObjectProjectStateHandler projectStateHandler;
    private final ProjectStateRegistry.SafeExclusiveLock isolationLock;
    private final WorkNodeAction isolateAction;

    public TransformationStep(Transformer transformer, TransformerInvoker transformerInvoker, DomainObjectProjectStateHandler projectStateHandler) {
        this.transformer = transformer;
        this.transformerInvoker = transformerInvoker;
        this.projectStateHandler = projectStateHandler;
        this.isolationLock = projectStateHandler.newExclusiveOperationLock();
        this.isolateAction = transformer.isIsolated() ? null : new WorkNodeAction() {
            @Nullable
            @Override
            public Project getProject() {
                return projectStateHandler.maybeGetOwningProject();
            }

            @Override
            public void run() {
                isolateExclusively();
            }
        };
    }

    @Override
    public boolean endsWith(Transformation otherTransform) {
        return this == otherTransform;
    }

    @Override
    public int stepsCount() {
        return 1;
    }

    @Override
    public Try<TransformationSubject> transform(TransformationSubject subjectToTransform, ExecutionGraphDependenciesResolver dependenciesResolver) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Transforming {} with {}", subjectToTransform.getDisplayName(), transformer.getDisplayName());
        }
        ImmutableList<File> inputArtifacts = subjectToTransform.getFiles();
        isolateTransformerParameters();
        return dependenciesResolver.forTransformer(transformer).flatMap(dependencies -> {
            ImmutableList.Builder<File> builder = ImmutableList.builder();
            for (File inputArtifact : inputArtifacts) {
                Try<ImmutableList<File>> result = transformerInvoker.invoke(transformer, inputArtifact, dependencies, subjectToTransform);

                if (result.getFailure().isPresent()) {
                    return Try.failure(result.getFailure().get());
                }
                builder.addAll(result.get());
            }
            return Try.successful(subjectToTransform.createSubjectFromResult(builder.build()));
        });
    }

    private void isolateTransformerParameters() {
        if (!transformer.isIsolated()) {
            if (!projectStateHandler.hasMutableProjectState()) {
                projectStateHandler.withLenientState(this::isolateExclusively);
            } else {
                isolateExclusively();
            }
        }
    }

    private void isolateExclusively() {
        isolationLock.withLock(() -> {
            if (!transformer.isIsolated()) {
                transformer.isolateParameters();
            }
        });
    }

    @Override
    public boolean requiresDependencies() {
        return transformer.requiresDependencies();
    }

    @Override
    public String getDisplayName() {
        return transformer.getDisplayName();
    }

    @Override
    public void visitTransformationSteps(Action<? super TransformationStep> action) {
        action.execute(this);
    }

    public ImmutableAttributes getFromAttributes() {
        return transformer.getFromAttributes();
    }

    @Override
    public String toString() {
        return String.format("%s@%s", transformer.getDisplayName(), transformer.getSecondaryInputHash());
    }

    public TaskDependencyContainer getDependencies() {
        return transformer;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        if (!transformer.isIsolated()) {
            context.add(isolateAction);
        }
        transformer.visitDependencies(context);
    }
}
