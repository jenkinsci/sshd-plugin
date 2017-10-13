Changelog
====

#### 2.2

Release Date: (Oct 13, 2017)

* [#20](https://github.com/jenkinsci/sshd-module/pull/20) -
Make SSHD startup synchronous (partially reverts changes in 2.1). 

#### 2.1

Release Date: (September 25, 2017)

* [#19](https://github.com/jenkinsci/sshd-module/pull/19) - Do not wait for `SSHD` to be fully up during Jenkins startup.
* [#19](https://github.com/jenkinsci/sshd-module/pull/19) - Avoid race condition between `SSHD#start` and `SSHD#setPort`.

#### 2.0

Release Date: (July 05, 2017)

* Update from SSHD Core `0.14.0` to Apache MINA SSHD `1.6.0`
  * See links to the integrated changes below
* [JENKINS-43668](https://issues.jenkins-ci.org/browse/JENKINS-43668) - 
Cleanup [Trilead SSH-2](https://github.com/jenkinsci/trilead-ssh2) usages in the SSHD Module.
* [JENKINS-39738](https://issues.jenkins-ci.org/browse/JENKINS-39738) -
Enable `aes192ctr` and `aes256ctr` ciphers if JVM supports them (unlimited-strength encryption).

Integrated SSHD Changes:

* [SSHD 1.0.0](https://issues.apache.org/jira/secure/ReleaseNote.jspa?version=12323302&styleName=&projectId=12310849)
* [SSHD 1.1.0](https://issues.apache.org/jira/secure/ReleaseNote.jspa?version=12333293&styleName=&projectId=12310849)
* [SSHD 1.1.1](https://issues.apache.org/jira/secure/ReleaseNote.jspa?version=12335067&styleName=&projectId=12310849)
* [SSHD 1.2.0](https://issues.apache.org/jira/secure/ReleaseNote.jspa?version=12334702&styleName=&projectId=12310849)
* [SSHD 1.3.0](https://issues.apache.org/jira/secure/ReleaseNote.jspa?version=12335499&styleName=&projectId=12310849)
* [SSHD 1.4.0](https://issues.apache.org/jira/secure/ReleaseNote.jspa?version=12338322&styleName=&projectId=12310849)
* SSHD 1.5.0 - N/A, the release has been burned
* [SSHD 1.6.0](https://issues.apache.org/jira/secure/ReleaseNote.jspa?version=12340583&styleName=&projectId=12310849)

##### 2.0 Compatibility notice

* Plugins merely using the `SshCommandFactory` extension point to offer SSH services (`workflowLibs.git`, for example) should be unaffected.
* Plugins which referred to arbitrary `org.apache.sshd.**` classes may not work unless updated to use the `pluginFirstClassLoader` flag to bundle their own private copy in a fixed version
  * We do not see such plugins in https://github.com/jenkinsci and other public repositories

#### 1.11

Release date: (Apr 07, 2017) => Jenkins `2.54`

* [JENKINS-33595](https://issues.jenkins-ci.org/browse/JENKINS-33595) -
Disable SSHD port by default on new installations.

#### 1.10

Release date: (Mar 11, 2017) => Jenkins `2.51`

* [PR #9](https://github.com/jenkinsci/sshd-module/pull/9) - 
Move SSH server port configuration to security options page.

#### 1.9

Release date: (Dec 11, 2016) => Jenkins `2.37`, backported to `2.32.2`

* [PR #8](https://github.com/jenkinsci/sshd-module/pull/8) - 
Update SSHD Core from `0.8.0` to `0.14.0`.
* [JENKINS-40362](https://issues.jenkins-ci.org/browse/JENKINS-40362) -
SSHD Module: Handshake was failing (wrong shared secret) 1 out of 256 times due to 
[SSHD-330](https://issues.apache.org/jira/browse/SSHD-330).

Integrated SSHD Core Changes:

* [SSHD Core 0.9](https://issues.apache.org/jira/secure/ReleaseNote.jspa?projectId=12310849&version=12323301)
* [SSHD Core 0.10.0](https://issues.apache.org/jira/secure/ReleaseNote.jspa?projectId=12310849&version=12324784)
* [SSHD Core 0.10.1](https://issues.apache.org/jira/secure/ReleaseNote.jspa?projectId=12310849&version=12326289)
* [SSHD Core 0.11.0](https://issues.apache.org/jira/secure/ReleaseNote.jspa?projectId=12310849&version=12326277)
* [SSHD Core 0.12.0](https://issues.apache.org/jira/secure/ReleaseNote.jspa?projectId=12310849&version=12326775)
* [SSHD Core 0.13.0](https://issues.apache.org/jira/secure/ReleaseNote.jspa?projectId=12310849&version=12327342)
* [SSHD Core 0.14.0](https://issues.apache.org/jira/secure/ReleaseNote.jspa?projectId=12310849&version=12329012)

#### Previous

There is no changelogs for this release and previous ones. 
See the commit history.
