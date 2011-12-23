package org.jenkinsci.main.modules.sshd.SSHD;

def f=namespace(lib.FormTagLib)

f.section(title:_("SSH Server")) {
    f.entry(title:_("SSHD Port"),field:"port") {
        f.serverTcpPort()
    }
}
