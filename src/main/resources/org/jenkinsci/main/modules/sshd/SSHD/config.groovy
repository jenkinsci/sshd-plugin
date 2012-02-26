package org.jenkinsci.main.modules.sshd.SSHD;

def f=namespace(lib.FormTagLib)

f.section(title:_("SSH Server")) {
    f.entry(title:_("SSHD Port"),field:"port") {
        f.serverTcpPort()
    }
    f.entry(title:"Idle Timeout (sec)",field:"idleTimeout") {
        f.number(clazz:"number",min:1,step:1);
    }
}
