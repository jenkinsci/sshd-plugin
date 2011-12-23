package org.jenkinsci.main.modules.sshd.SSHD;

def f=namespace(lib.FormTagLib)

f.entry(title:_("SSHD Port"),field:"port") {
    f.serverTcpPort()
}
