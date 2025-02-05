package org.jenkinsci.main.modules.sshd.PortAdvertiser;

def v = my.endpoint;
if (v!=null)
    response2.addHeader("X-SSH-Endpoint",v);
