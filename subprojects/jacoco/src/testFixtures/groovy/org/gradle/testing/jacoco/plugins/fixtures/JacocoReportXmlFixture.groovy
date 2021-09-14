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

package org.gradle.testing.jacoco.plugins.fixtures

import groovy.transform.ToString
import groovy.transform.TupleConstructor
import groovy.xml.XmlSlurper
import org.gradle.test.fixtures.file.TestFile

class JacocoReportXmlFixture {
    private final xml
    private final List<Coverage> classes

    JacocoReportXmlFixture(TestFile reportXmlFile) {
        reportXmlFile.assertIsFile()
        def slurper = new XmlSlurper(false, false, true)
        slurper.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        this.xml = slurper.parse(reportXmlFile)
        this.classes = []
        xml*.'package'.each { pkg ->
            classes.addAll(pkg.'class'.collect {
                def name = it.@name.toString()
                def classCounter = it.counter.find { it.@type == "CLASS" }
                def missed = Integer.parseInt(classCounter.@missed.toString())
                def covered = Integer.parseInt(classCounter.@covered.toString())
                return new Coverage(name, covered, missed)
            })
        }
    }

    void assertHasClassCoverage(String clazz, int covered=1) {
        def coverage = findClass(clazz)
        assert coverage
        assert coverage.covered == covered
    }

    Coverage findClass(String clazz) {
        def searchFor = clazz.replace('.', '/')
        return classes.find(candidate -> candidate.name == searchFor)
    }

    @ToString
    @TupleConstructor
    static class Coverage {
        String name
        int covered
        int missed

        boolean isCompleteCoverage() {
            missed == 0
        }
    }
}
