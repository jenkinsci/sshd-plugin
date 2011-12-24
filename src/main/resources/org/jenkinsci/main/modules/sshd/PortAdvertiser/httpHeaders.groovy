package org.jenkinsci.main.modules.sshd.PortAdvertiser;

def v = my.endpoint;
if (v!=null)
    response.addHeader("X-SSH-Endpoint",v);
