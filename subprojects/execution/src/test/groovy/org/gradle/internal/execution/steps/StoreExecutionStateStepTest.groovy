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

package org.gradle.internal.execution.steps

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSortedMap
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.internal.Try
import org.gradle.internal.execution.ExecutionResult
import org.gradle.internal.execution.history.AfterExecutionState
import org.gradle.internal.execution.history.BeforeExecutionState
import org.gradle.internal.execution.history.ExecutionHistoryStore
import org.gradle.internal.execution.history.PreviousExecutionState
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.impl.ImplementationSnapshot

class StoreExecutionStateStepTest extends StepSpec<BeforeExecutionContext> implements SnasphotterFixture {
    def executionHistoryStore = Mock(ExecutionHistoryStore)

    def originMetadata = Mock(OriginMetadata)
    def beforeExecutionState = Stub(BeforeExecutionState) {
        getImplementation() >> ImplementationSnapshot.of("Test", HashCode.fromInt(123))
        getAdditionalImplementations() >> ImmutableList.of()
        getInputProperties() >> ImmutableSortedMap.of()
        getInputFileProperties() >> ImmutableSortedMap.of()
    }

    def outputFile = file("output.txt").text = "output"
    def outputFilesProducedByWork = snapshotsOf(output: outputFile)

    def step = new StoreExecutionStateStep<BeforeExecutionContext>(delegate)
    def delegateResult = Mock(CurrentSnapshotResult)

    @Override
    protected BeforeExecutionContext createContext() {
        Stub(BeforeExecutionContext)
    }

    def setup() {
        _ * context.history >> Optional.of(executionHistoryStore)
    }

    def "output snapshots are stored after successful execution"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult
        1 * delegate.execute(work, context) >> delegateResult

        then:
        1 * delegateResult.afterExecutionState >> Optional.of(Mock(AfterExecutionState) {
            _ * getOutputFilesProducedByWork() >> this.outputFilesProducedByWork
        })
        1 * delegateResult.originMetadata >> originMetadata
        _ * context.beforeExecutionState >> Optional.of(beforeExecutionState)
        _ * delegateResult.executionResult >> Try.successful(Mock(ExecutionResult))

        then:
        interaction { expectStore(true, outputFilesProducedByWork) }
        0 * _
    }

    def "output snapshots are stored after failed execution when there's no previous state available"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult
        1 * delegate.execute(work, context) >> delegateResult

        then:
        1 * delegateResult.afterExecutionState >> Optional.of(Mock(AfterExecutionState) {
            1 * getOutputFilesProducedByWork() >> this.outputFilesProducedByWork
        })
        _ * context.beforeExecutionState >> Optional.of(beforeExecutionState)
        _ * delegateResult.executionResult >> Try.failure(new RuntimeException("execution error"))
        _ * context.previousExecutionState >> Optional.empty()
        1 * delegateResult.originMetadata >> originMetadata

        then:
        interaction { expectStore(false, outputFilesProducedByWork) }
        0 * _
    }

    def "output snapshots are stored after failed execution with changed outputs"() {
        def previousExecutionState = Mock(PreviousExecutionState)

        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult
        1 * delegate.execute(work, context) >> delegateResult

        then:
        1 * delegateResult.afterExecutionState >> Optional.of(Mock(AfterExecutionState) {
            _ * getOutputFilesProducedByWork() >> this.outputFilesProducedByWork
        })
        _ * context.beforeExecutionState >> Optional.of(beforeExecutionState)
        _ * delegateResult.executionResult >> Try.failure(new RuntimeException("execution error"))
        _ * context.previousExecutionState >> Optional.of(previousExecutionState)
        1 * previousExecutionState.outputFilesProducedByWork >> snapshotsOf([:])
        1 * delegateResult.originMetadata >> originMetadata

        then:
        interaction { expectStore(false, outputFilesProducedByWork) }
        0 * _
    }

    def "output snapshots are not stored after failed execution with unchanged outputs"() {
        def previousExecutionState = Mock(PreviousExecutionState)

        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult
        1 * delegate.execute(work, context) >> delegateResult

        then:
        1 * delegateResult.afterExecutionState >> Optional.of(Mock(AfterExecutionState) {
            _ * getOutputFilesProducedByWork() >> this.outputFilesProducedByWork
        })
        _ * context.beforeExecutionState >> Optional.of(beforeExecutionState)
        _ * delegateResult.executionResult >> Try.failure(new RuntimeException("execution error"))
        _ * context.previousExecutionState >> Optional.of(previousExecutionState)
        1 * previousExecutionState.outputFilesProducedByWork >> outputFilesProducedByWork
        0 * _
    }

    def "does not store untracked outputs"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult
        1 * delegate.execute(work, context) >> delegateResult

        then:
        1 * executionHistoryStore.remove(identity.uniqueId)
        _ * delegateResult.afterExecutionState >> Optional.empty()
        0 * _
    }

    void expectStore(boolean successful, ImmutableSortedMap<String, FileSystemSnapshot> finalOutputs) {
        1 * executionHistoryStore.store(
            identity.uniqueId,
            originMetadata,
            successful,
            { AfterExecutionState executionState ->
                executionState.outputFilesProducedByWork == finalOutputs
            }
        )
    }
}
