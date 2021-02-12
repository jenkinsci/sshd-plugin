ARG TAG=2.267
FROM jenkins/jenkins:${TAG}
USER root
COPY ./jenkins_home/plugins.txt /usr/share/jenkins/ref/
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt
COPY ./jenkins_home/jenkins.yaml /usr/share/jenkins/ref/jenkins.yaml
COPY ./jobs /usr/share/jenkins/ref/jobs
COPY ./jenkins_home/plugins/* /usr/share/jenkins/ref/plugins/
RUN chown -R jenkins:jenkins /usr/share/jenkins/ref/
RUN touch /var/run/docker.sock \
  && chown jenkins:jenkins /var/run/docker.sock

COPY ./war/* /usr/share/jenkins/
USER jenkins
