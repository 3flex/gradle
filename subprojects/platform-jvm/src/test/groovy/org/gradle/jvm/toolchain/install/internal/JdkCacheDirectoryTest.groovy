/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.install.internal

import org.gradle.api.internal.file.temp.DefaultTemporaryFileProvider
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.internal.file.TestFiles
import org.gradle.cache.FileLock
import org.gradle.cache.FileLockManager
import org.gradle.cache.LockOptions
import org.gradle.initialization.GradleUserHomeDirProvider
import org.gradle.util.internal.Resources
import org.junit.Rule
import spock.lang.Specification
import spock.lang.TempDir

class JdkCacheDirectoryTest extends Specification {

    @TempDir
    public File temporaryFolder

    @Rule
    public final Resources resources = new Resources()

    def "handles non-exisiting jdk directory when listing java homes"() {
        given:
        def jdkCacheDirectory = new JdkCacheDirectory(newHomeDirProvider(), Mock(FileOperations), mockLockManager())

        when:
        def homes = jdkCacheDirectory.listJavaHomes()

        then:
        homes.isEmpty()
    }

    def "lists jdk directories when listing java homes"() {
        given:
        def jdkCacheDirectory = new JdkCacheDirectory(newHomeDirProvider(), Mock(FileOperations), mockLockManager())
        def install1 = new File(temporaryFolder, "jdks/jdk-123").tap { mkdirs() }
        new File(install1, "provisioned.ok").createNewFile()

        def install2 = new File(temporaryFolder, "jdks/jdk-345").tap { mkdirs() }
        new File(install2, "provisioned.ok").createNewFile()

        def install3 = new File(temporaryFolder, "jdks/jdk-mac/Contents/Home").tap { mkdirs() }
        new File(temporaryFolder, "jdks/jdk-mac/provisioned.ok").createNewFile()

        new File(temporaryFolder, "jdks/notReady").tap { mkdirs() }

        when:
        def homes = jdkCacheDirectory.listJavaHomes()

        then:
        homes.containsAll([install1, install2, install3])
    }

    def "provisions jdk from tar.gz archive"() {
        def jdkArchive = resources.getResource("jdk.tar.gz")
        def jdkCacheDirectory = new JdkCacheDirectory(newHomeDirProvider(), TestFiles.fileOperations(temporaryFolder, tmpFileProvider()), mockLockManager())

        when:
        def installedJdk = jdkCacheDirectory.provisionFromArchive(jdkArchive)

        then:
        installedJdk.exists()
        new File(installedJdk, "file").exists()
        new File(installedJdk, "provisioned.ok").exists()
    }

    def "provisions jdk from zip archive"() {
        def jdkArchive = resources.getResource("jdk.zip")
        def jdkCacheDirectory = new JdkCacheDirectory(newHomeDirProvider(), TestFiles.fileOperations(temporaryFolder, tmpFileProvider()), mockLockManager())

        when:
        def installedJdk = jdkCacheDirectory.provisionFromArchive(jdkArchive)

        then:
        installedJdk.exists()
        println installedJdk
        new File(installedJdk, "file").exists()
        new File(installedJdk, "provisioned.ok").exists()
    }

    private GradleUserHomeDirProvider newHomeDirProvider() {
        new GradleUserHomeDirProvider() {
            @Override
            File getGradleUserHomeDirectory() {
                return temporaryFolder
            }
        }
    }

    TemporaryFileProvider tmpFileProvider() {
        new DefaultTemporaryFileProvider({ temporaryFolder })
    }

    FileLockManager mockLockManager() {
        def lockManager = Mock(FileLockManager)
        def lock = Mock(FileLock)
        lockManager.lock(_ as File, _ as LockOptions, _ as String, _ as String) >> lock
        lockManager
    }
}
