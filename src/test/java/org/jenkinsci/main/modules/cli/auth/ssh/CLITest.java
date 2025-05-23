/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.main.modules.cli.auth.ssh;

import hudson.Extension;
import hudson.Launcher;
import hudson.Proc;
import hudson.cli.CLICommand;
import hudson.model.FreeStyleProject;
import hudson.model.UnprotectedRootAction;
import hudson.model.User;
import hudson.security.csrf.CrumbExclusion;
import hudson.util.StreamTaskListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.sshd.common.util.io.ModifiableFileWatcher;
import org.htmlunit.WebResponse;
import org.jenkinsci.main.modules.sshd.SSHD;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.HttpResponses.HttpResponseException;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests CLI operation with the classes in the {@code org.jenkinsci.main.modules.sshd} package.
 * Extracted from https://github.com/jenkinsci/jenkins/blob/e681b4ff928435a500dcfb372cc9fc15b1c94413/test/src/test/java/hudson/cli/CLITest.java
 */
@WithJenkins
class CLITest {

    @TempDir
    private File tmp;

    private File home;
    private File jar;

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    /**
     * Sets up a fake {@code user.home} so that tests {@code -ssh} mode does not get confused by the developer’s real {@code ~/.ssh/known_hosts}.
     */
    private File tempHome() throws IOException {
        home = newFolder(tmp, "junit");
        // Seems it gets created automatically but with inappropriate permissions:
        File known_hosts = new File(new File(home, ".ssh"), "known_hosts");
        assumeTrue(known_hosts.getParentFile().mkdir());
        assumeTrue(known_hosts.createNewFile());
        assumeTrue(known_hosts.setWritable(false, false));
        assumeTrue(known_hosts.setWritable(true, true));
        try {
            Files.getOwner(known_hosts.toPath());
        } catch (IOException x) {
            assumeTrue(false, "Sometimes on Windows KnownHostsServerKeyVerifier.acceptIncompleteHostKeys says WARNING: Failed (FileSystemException) to reload server keys from …\\\\.ssh\\\\known_hosts: … Incorrect function. Error: " + x);
        }
        assumeTrue(ModifiableFileWatcher.validateStrictConfigFilePermissions(known_hosts.toPath()) == null,
                "or on Windows DefaultKnownHostsServerKeyVerifier.reloadKnownHosts says invalid file permissions: Owner violation (Administrators)");
        return home;
    }

    @Issue("JENKINS-41745")
    @Test
    void strictHostKey() throws Exception {
        Launcher.LocalLauncher localLauncher = new Launcher.LocalLauncher(StreamTaskListener.fromStderr());
        String jenkinsUrl = r.getURL().toString();
        home = tempHome();
        grabCliJar();
        String sshCliJar = jar.getAbsolutePath();

        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("admin"));
        SSHD.get().setPort(0);
        File privkey = File.createTempFile("id_rsa", null, tmp);
        FileUtils.copyURLToFile(CLITest.class.getResource("id_rsa"), privkey);
        User.getById("admin", true).addProperty(new UserPropertyImpl(IOUtils.toString(CLITest.class.getResource("id_rsa.pub"), StandardCharsets.UTF_8)));
        assertNotEquals(0, new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(
                "java", "-Duser.home=" + home, "-jar", jar.getAbsolutePath(), "-s", r.getURL().toString(), "-ssh", "-user", "admin", "-i", privkey.getAbsolutePath(), "-strictHostKey", "who-am-i"
        ).stdout(System.out).stderr(System.err).join());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertEquals(0, new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(
                "java", "-Duser.home=" + home, "-jar", jar.getAbsolutePath(), "-s", r.getURL().toString(), "-ssh", "-user", "admin", "-i", privkey.getAbsolutePath(), "-logger", "FINEST", "who-am-i"
        ).stdout(baos).stderr(System.err).join());
        assertThat(baos.toString(), containsString("Authenticated as: admin"));
        baos = new ByteArrayOutputStream();
        assertEquals(0, new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(
                "java", "-Duser.home=" + home, "-jar", jar.getAbsolutePath(), "-s", r.getURL().toString()./* just checking */replaceFirst("/$", ""), "-ssh", "-user", "admin", "-i", privkey.getAbsolutePath(), "-strictHostKey", "who-am-i"
        ).stdout(baos).stderr(System.err).join());
        assertThat(baos.toString(), containsString("Authenticated as: admin"));
    }

    private void grabCliJar() throws IOException {
        jar = File.createTempFile("jenkins-cli.jar", null, tmp);
        FileUtils.copyURLToFile(r.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
    }

    @Issue("JENKINS-41745")
    @Test
    void interrupt() throws Exception {
        home = tempHome();
        grabCliJar();

        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("admin"));
        SSHD.get().setPort(0);
        File privkey = File.createTempFile("id_rsa", null, tmp);
        FileUtils.copyURLToFile(CLITest.class.getResource("id_rsa"), privkey);
        User.getById("admin", true).addProperty(new UserPropertyImpl(IOUtils.toString(CLITest.class.getResource("id_rsa.pub"), StandardCharsets.UTF_8)));
        FreeStyleProject p = r.createFreeStyleProject("p");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        p.getBuildersList().add(new SleepBuilder(TimeUnit.MINUTES.toMillis(2)));
        List<String> args = Arrays.asList("java", "-Duser.home=" + home, "-jar", jar.getAbsolutePath(), "-s", r.getURL().toString(), "-ssh", "-user", "admin", "-i", privkey.getAbsolutePath(), "build", "-s", "-v", "p");
        Proc proc = new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(args).stdout(new TeeOutputStream(baos, System.out)).stderr(System.err).start();
        while (!baos.toString().contains("Sleeping ")) {
            if (!proc.isAlive()) {
                throw new AssertionError("Process failed to start with " + proc.join());
            }
            Thread.sleep(100);
        }
        System.err.println("Killing client");
        proc.kill();
        r.waitForCompletion(p.getLastBuild());
    }

    @Issue("JENKINS-68541")
    @Test
    void outputStream() throws Exception {
        home = tempHome();
        grabCliJar();

        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("admin"));
        SSHD.get().setPort(0);
        File privkey = File.createTempFile("id_rsa", null, tmp);
        FileUtils.copyURLToFile(CLITest.class.getResource("id_rsa"), privkey);
        User.getById("admin", true).addProperty(new UserPropertyImpl(IOUtils.toString(CLITest.class.getResource("id_rsa.pub"), StandardCharsets.UTF_8)));
        StreamTaskListener stl = StreamTaskListener.fromStderr();
        List<String> args = Arrays.asList("java", "-Duser.home=" + home, "-jar", jar.getAbsolutePath(), "-s", r.getURL().toString(), "-ssh", "-user", "admin", "-i", privkey.getAbsolutePath(), "close-stdout-stream");
        int ret = new Launcher.LocalLauncher(stl).launch().cmds(args)
                .stdout(System.out)
                .stderr(System.err)
                .start()
                .joinWithTimeout(5, TimeUnit.SECONDS, stl);
        assertEquals(0, ret);
    }

    @Test
    @Issue("JENKINS-44361")
    void reportNotJenkins() throws Exception {
        home = tempHome();
        grabCliJar();

        String url = r.getURL().toExternalForm() + "not-jenkins/";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int ret = new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(
                "java", "-Duser.home=" + home, "-jar", jar.getAbsolutePath(), "-s", url, "-ssh", "-user", "asdf", "who-am-i"
        ).stdout(baos).stderr(baos).join();

        assertThat(baos.toString(), containsString("There's no Jenkins running at"));
        assertNotEquals(0, ret);
    }

    @TestExtension("reportNotJenkins")
    public static final class NoJenkinsAction extends CrumbExclusion implements UnprotectedRootAction, StaplerProxy {

        @Override
        public String getIconFileName() {
            return "not-jenkins";
        }

        @Override
        public String getDisplayName() {
            return "not-jenkins";
        }

        @Override
        public String getUrlName() {
            return "not-jenkins";
        }

        @Override
        public Object getTarget() {
            doDynamic(Stapler.getCurrentRequest2(), Stapler.getCurrentResponse2());
            return this;
        }

        public void doDynamic(StaplerRequest2 req, StaplerResponse2 rsp) {
            rsp.setStatus(200);
        }

        @Override // Permit access to cli-proxy/XXX without CSRF checks
        public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
            chain.doFilter(request, response);
            return true;
        }
    }

    @Test
    @Issue("JENKINS-44361")
    void redirectToEndpointShouldBeFollowed() throws Exception {
        home = tempHome();
        grabCliJar();

        // Enable CLI over SSH
        SSHD sshd = GlobalConfiguration.all().get(SSHD.class);
        sshd.setPort(0); // random
        sshd.start();

        // Sanity check
        JenkinsRule.WebClient wc = r.createWebClient()
                .withRedirectEnabled(false)
                .withThrowExceptionOnFailingStatusCode(false);

        WebResponse rsp = wc.goTo("cli-proxy/").getWebResponse();
        assertEquals(HttpURLConnection.HTTP_MOVED_TEMP, rsp.getStatusCode(), rsp.getContentAsString());
        assertNull(rsp.getResponseHeaderValue("X-Jenkins"), rsp.getContentAsString());
        assertNull(rsp.getResponseHeaderValue("X-Jenkins-CLI-Port"), rsp.getContentAsString());
        assertNull(rsp.getResponseHeaderValue("X-SSH-Endpoint"), rsp.getContentAsString());

        String url = r.getURL().toString() + "cli-proxy/";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int ret = new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(
                "java", "-Duser.home=" + home, "-jar", jar.getAbsolutePath(), "-s", url, "-ssh", "-user", "asdf", "who-am-i"
        ).stdout(baos).stderr(baos).join();

        //assertThat(baos.toString(), containsString("There's no Jenkins running at"));
        assertThat(baos.toString(), containsString("Authenticated as: anonymous"));
        assertEquals(0, ret);
    }

    @TestExtension("redirectToEndpointShouldBeFollowed")
    public static final class CliProxyAction extends CrumbExclusion implements UnprotectedRootAction, StaplerProxy {

        @Override
        public String getIconFileName() {
            return "cli-proxy";
        }

        @Override
        public String getDisplayName() {
            return "cli-proxy";
        }

        @Override
        public String getUrlName() {
            return "cli-proxy";
        }

        @Override
        public Object getTarget() {
            throw doDynamic(Stapler.getCurrentRequest2(), Stapler.getCurrentResponse2());
        }

        public HttpResponseException doDynamic(StaplerRequest2 req, StaplerResponse2 rsp) {
            final String url = req.getRequestURIWithQueryString().replaceFirst("/cli-proxy", "");
            // Custom written redirect so no traces of Jenkins are present in headers
            return new HttpResponseException() {
                @Override
                public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException {
                    rsp.setHeader("Location", url);
                    rsp.setContentType("text/html");
                    rsp.setStatus(HttpURLConnection.HTTP_MOVED_TEMP);
                    PrintWriter w = rsp.getWriter();
                    w.append("Redirect to ").append(url);
                }
            };
        }

        @Override // Permit access to cli-proxy/XXX without CSRF checks
        public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
            chain.doFilter(request, response);
            return true;
        }
    }

    @Extension
    public static class CloseStdoutStreamCommand extends CLICommand {

        @Override
        public String getShortDescription() {
            return "Close stdout";
        }

        @Override
        protected int run() {
            stdout.close();
            return 0;
        }
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }
}
