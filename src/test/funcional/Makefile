JENKINS_TAG ?= 2.267
VENV ?= .venv
PYTHON ?= python3
PIP ?= pip3

export JENKINS_VERSION:=$(JENKINS_TAG)

JOBS_ENABLED ?= false
JENKINS_URL = http://localhost:8080

.PHONY: help
help:
	@echo "Targets:"
	@echo "you need to add the entry '127.0.0.1 jenkins.example.com' to yout /etc/hosts file"
	@echo "http://jenkins.example.com:8080 should be the Jenkins URL"
	@echo ""
	@grep '^## @help' Makefile|cut -d ":" -f 2-3|( (sort|column -s ":" -t) || (sort|tr ":" "\t") || (tr ":" "\t"))

## @help:start:Start the Jenkins instance.
.PHONY: start
start: gen-jcasc-file
	docker-compose build --force-rm --no-cache
	docker-compose up --no-start
	docker-compose start
#	docker-compose start --force-recreate --build --remove-orphans;

## @help:stop:Stop the Jenkins instance.
.PHONY: stop
stop:
	-docker-compose down

## @help:clean:Delete the Jenkins docker image an volumes.
.PHONY: clean
clean: stop
	-docker volume rm jenkins_home

## @help:virtualenv:Create a Python virtual environment.
.PHONY: virtualenv
virtualenv:
	$(PYTHON) --version
	test -d $(VENV) || virtualenv -q --python=$(PYTHON) $(VENV);\
	source $(VENV)/bin/activate;\
	$(PIP) install -r requirements.txt;

## @help:gen-jcasc-file:Generates the JCasC file with the SSH Privatekey
.PHONY: gen-jcasc-file
gen-jcasc-file: JCASC_FILE="./jenkins/jenkins_home/jenkins.yaml"
gen-jcasc-file: virtualenv
	source $(VENV)/bin/activate;\
	JOBS_ENABLED="$(JOBS_ENABLED)" \
	DOCKER_URI="$(DOCKER_URI)" \
	j2 --format=env $(JCASC_FILE).j2 -o $(JCASC_FILE) --customize customization.py

## @help:download-plugin-manager:Download the Jenkins plugin Manager
.PHONY: download-plugin-manager
download-plugin-manager:
	curl -L -o jenkins/jenkins_home/jenkins-plugin-manager.jar https://github.com/jenkinsci/plugin-installation-manager-tool/releases/latest

## @help:dependencies:Download/copy the dependencies to build Docker images.
.PHONY: dependencies
dependencies:
	# TODO change before merge and after https://github.com/jenkinsci/jenkins/pull/5049 and https://github.com/jenkinsci/sshd-module/pull/38
	export JENKINS_URL="https://ci.jenkins.io/job/Core/job/jenkins/job/PR-5049/22/artifact/org/jenkins-ci/main/jenkins-war/2.270-rc30619.ae60983c246a/jenkins-war-2.270-rc30619.ae60983c246a.war"; \
	curl -fsSL -o jenkins/war/jenkins.war $${JENKINS_URL};
	export SSHD_MODULE_URL="https://repo.jenkins-ci.org/incrementals/org/jenkins-ci/modules/sshd/3.0-rc166.9325e5d37325/sshd-3.0-rc166.9325e5d37325.hpi"; \
	curl -fsSL -o jenkins/jenkins_home/plugins/sshd.jpi $${SSHD_MODULE_URL};
	# cp ../../../target/sshd.jpi jenkins/jenkins_home/plugins/sshd.jpi

## @help:test:Run a set of ssh connection tests againts the SSH Jenkins instance.
.PHONY: test
test:
	@rm -fr jenkins-cli.jar workflowLibs
	@chmod 0600 ./rsa-512-key
	@curl -fsSL -O ${JENKINS_URL}/jnlpJars/jenkins-cli.jar
	JENKINS_URL=$(JENKINS_URL) \
	pytest -o junit_family=xunit2 --junitxml=junit-report.xml
