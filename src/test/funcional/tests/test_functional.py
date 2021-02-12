
def test_jenkins_cli(caplog):
    import os
    stream = os.popen("""
      java -jar ./jenkins-cli.jar -ssh -user admin -i ./rsa-512-key -s ${JENKINS_URL} who-am-i
    """)
    output = stream.read()
    print(output)
    assert output.find("authenticated")


def test_ssh_connection():
    import os
    stream = os.popen("""ssh -p 2222 \
      -i ./rsa-512-key \
      -o StrictHostKeyChecking=no \
      -o UserKnownHostsFile=/dev/null \
      admin@localhost who-am-i""")
    output = stream.read()
    print(output)
    assert output.find("authenticated")

def test_jenkins_git():
    import os
    stream = os.popen("""
    	GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -i ./rsa-512-key" \
      git clone ssh://admin@localhost:2222/workflowLibs.git
    """)
    output = stream.read()
    print(output)
    assert output.find("cloned an empty repository")
