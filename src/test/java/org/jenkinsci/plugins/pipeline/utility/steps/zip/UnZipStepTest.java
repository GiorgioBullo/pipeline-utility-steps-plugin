/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 CloudBees Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.jenkinsci.plugins.pipeline.utility.steps.zip;

import hudson.model.Label;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.net.URL;
import java.net.URLDecoder;

import static org.jenkinsci.plugins.pipeline.utility.steps.FilenameTestsUtils.separatorsToSystemEscaped;
import static org.junit.Assert.assertFalse;

/**
 * Tests for {@link UnZipStep}.
 *
 * @author Robert Sandell &lt;rsandell@cloudbees.com&gt;.
 */
public class UnZipStepTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void setup() throws Exception {
        j.createOnlineSlave(Label.get("slaves"));
    }

    @Test
    public void configRoundTrip() throws Exception {
        UnZipStep step = new UnZipStep("target/my.zip");
        step.setDir("base/");
        step.setGlob("**/*.zip");
        step.setRead(true);
        step.setQuiet(false);
        step.setCharset("");

        UnZipStep step2 = new StepConfigTester(j).configRoundTrip(step);
        j.assertEqualDataBoundBeans(step, step2);
        assertFalse(step2.isTest());
    }

    @Test
    public void simpleUnZip() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node('slaves') {\n" +
                        "  dir('zipIt') {\n" +
                        "    writeFile file: 'hello.txt', text: 'Hello World!'\n" +
                        "    writeFile file: 'hello.dat', text: 'Hello World!'\n" +
                        "    dir('two') {\n" +
                        "      writeFile file: 'hello.txt', text: 'Hello World2!'\n" +
                        "    }\n" +
                        "    zip zipFile: '../hello.zip'\n" +
                        "  }\n" +
                        "  dir('unzip') {\n" +
                        "    unzip '../hello.zip'\n" +
                        "    String txt = readFile 'hello.txt'\n" +
                        "    echo \"Reading: ${txt}\"\n" +
                        "  }\n" +
                        "}", true));
        WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        j.assertLogContains("Extracting: hello.txt ->", run);
        j.assertLogContains("Extracting: two/hello.txt ->", run);
        j.assertLogContains("Extracting: hello.dat ->", run);
        j.assertLogContains("Reading: Hello World!", run);
    }

    @Test
    public void globUnZip() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node('slaves') {\n" +
                        "  dir('zipIt') {\n" +
                        "    writeFile file: 'hello.txt', text: 'Hello World!'\n" +
                        "    writeFile file: 'hello.dat', text: 'Hello World!'\n" +
                        "    dir('two') {\n" +
                        "      writeFile file: 'hello.txt', text: 'Hello World2!'\n" +
                        "    }\n" +
                        "    zip zipFile: '../hello.zip'\n" +
                        "  }\n" +
                        "  dir('unzip') {\n" +
                        "    unzip zipFile: '../hello.zip', glob: '**/*.txt'\n" +
                        "    String txt = readFile 'hello.txt'\n" +
                        "    echo \"Reading: ${txt}\"\n" +
                        "    txt = readFile 'two/hello.txt'\n" +
                        "    echo \"Reading: ${txt}\"\n" +
                        "  }\n" +
                        "}", true));
        WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        j.assertLogContains("Extracting: hello.txt ->", run);
        j.assertLogContains("Extracting: two/hello.txt ->", run);
        j.assertLogNotContains("Extracting: hello.dat ->", run);
        j.assertLogContains("Reading: Hello World!", run);
        j.assertLogContains("Reading: Hello World2!", run);
    }

    @Test
    public void globReading() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node('slaves') {\n" +
                        "  dir('zipIt') {\n" +
                        "    writeFile file: 'hello.txt', text: 'Hello World!'\n" +
                        "    writeFile file: 'hello.dat', text: 'Hello World!'\n" +
                        "    zip zipFile: '../hello.zip'\n" +
                        "  }\n" +
                        "  dir('unzip') {\n" +
                        "    def txt = unzip zipFile: '../hello.zip', glob: '**/hello.txt', read: true\n" +
                        "    echo \"Text: ${txt.values().join('\\n')}\"\n" +
                        "  }\n" +
                        "}", true));
        WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        j.assertLogContains("Reading: hello.txt", run);
        j.assertLogNotContains("Reading: hello.dat", run);
        j.assertLogContains("Text: Hello World!", run);
    }

    @Test
    public void globReadingMore() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node('slaves') {\n" +
                        "  dir('zipIt') {\n" +
                        "    writeFile file: 'hello.txt', text: 'Hello World!'\n" +
                        "    writeFile file: 'hello.dat', text: 'Hello Data World!'\n" +
                        "  }\n" +
                        "  zip zipFile: 'hello.zip', dir: 'zipIt'\n" +
                        "  dir('unzip') {\n" +
                        "    def txt = unzip zipFile: '../hello.zip', read: true\n" +
                        "    echo \"Text: ${txt['hello.txt']}\"\n" +
                        "    echo \"Text: ${txt['hello.dat']}\"\n" +
                        "  }\n" +
                        "}", true));
        WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        j.assertLogContains("Reading: hello.txt", run);
        j.assertLogContains("Reading: hello.dat", run);
        j.assertLogContains("Text: Hello World!", run);
        j.assertLogContains("Text: Hello Data World!", run);
    }

    @Test
    public void zipTest() throws Exception {
        Assume.assumeTrue("Can only run in a gnu unix environment", File.pathSeparatorChar == ':');
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node('slaves') {\n" +
                        "  dir('zipIt') {\n" +
                        "    writeFile file: 'hello.txt', text: 'Hello World!'\n" +
                        "    writeFile file: 'hello.dat', text: 'Hello Data World!'\n" +
                        "    dir('two') {\n" +
                        "      writeFile file: 'hello.txt', text: 'Hello World2!'\n" +
                        "    }\n" +
                        "    zip zipFile: '../hello.zip'\n" +
                        "  }\n" +
                        "  sh 'head -c $(($(cat hello.zip | wc -c) / 2)) hello.zip > corrupt.zip'\n" +
                        "  def result = unzip zipFile: 'corrupt.zip', test: true \n" +
                        "  if (result != false)\n" +
                        "      error('Should be corrupt!')\n" +
                        "}", true));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    }

    @Test
    public void testZipTestingBrokenZip() throws Exception {
        /*
         This test uses a prepared zip file that has a single flipped bit inside the byte stream of the zip file entry.
         The test method has to find this error. This will require the stream to be read, because the CRC check is able
         to reveal this error.
         */
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        URL resource = getClass().getResource("test_broken.zip");
        String zip = new File(URLDecoder.decode(resource.getPath(), "UTF-8")).getAbsolutePath().replace('\\', '/');
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  def result = unzip zipFile: '" + separatorsToSystemEscaped(zip) + "', test: true\n" +
                "  if (result)\n" +
                "      error('Should be corrupt!')\n" +
                "}", true));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    }

    @Test
    public void testZipTestingOkayZip() throws Exception {
        /*
         This test uses a prepared zip file without any errors.
         */
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        URL resource = getClass().getResource("test_ok.zip");
        String zip = new File(URLDecoder.decode(resource.getPath(), "UTF-8")).getAbsolutePath().replace('\\', '/');
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  def result = unzip zipFile: '" + separatorsToSystemEscaped(zip) + "', test: true\n" +
                "  if (!result)\n" +
                "      error('Should be okay!')\n" +
                "}", true));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    }

    @Test
    public void unzipQuiet() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node('slaves') {\n" +
                        "  dir('zipIt') {\n" +
                        "    writeFile file: 'hello.txt', text: 'Hello World!'\n" +
                        "    writeFile file: 'hello.dat', text: 'Hello World!'\n" +
                        "    dir('two') {\n" +
                        "      writeFile file: 'hello.txt', text: 'Hello World2!'\n" +
                        "    }\n" +
                        "    zip zipFile: '../hello.zip'\n" +
                        "  }\n" +
                        "  dir('unzip') {\n" +
                        "    unzip zipFile: '../hello.zip', quiet: true\n" +
                        "    String txt = readFile 'hello.txt'\n" +
                        "    echo \"Reading: ${txt}\"\n" +
                        "    txt = readFile 'two/hello.txt'\n" +
                        "    echo \"Reading: ${txt}\"\n" +
                        "  }\n" +
                        "}", true));
        WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        j.assertLogNotContains("Extracting: hello.txt ->", run);
        j.assertLogNotContains("Extracting: two/hello.txt ->", run);
        j.assertLogNotContains("Extracting: hello.dat ->", run);
        j.assertLogContains("Extracted: 3 files", run);
        j.assertLogContains("Reading: Hello World!", run);
        j.assertLogContains("Reading: Hello World2!", run);
    }

    @Test
    public void unzipQuietReading() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node('slaves') {\n" +
                        "  dir('zipIt') {\n" +
                        "    writeFile file: 'hello.txt', text: 'Hello World!'\n" +
                        "    writeFile file: 'hello.dat', text: 'Hello World!'\n" +
                        "    zip zipFile: '../hello.zip'\n" +
                        "  }\n" +
                        "  dir('unzip') {\n" +
                        "    def txt = unzip zipFile: '../hello.zip', quiet: true, read: true\n" +
                        "    echo \"Text: ${txt.values().join('\\n')}\"\n" +
                        "  }\n" +
                        "}", true));
        WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        j.assertLogNotContains("Reading: hello.txt", run);
        j.assertLogNotContains("Reading: hello.dat", run);
        j.assertLogContains("Read: 2 files", run);
        j.assertLogContains("Text: Hello World!", run);
    }
}
