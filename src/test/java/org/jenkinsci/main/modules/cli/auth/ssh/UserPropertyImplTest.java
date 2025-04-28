package org.jenkinsci.main.modules.cli.auth.ssh;

import hudson.model.User;
import hudson.util.FormValidation;
import org.htmlunit.FailingHttpStatusCodeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.htmlunit.html.HtmlFormUtil.submit;
import static org.junit.jupiter.api.Assertions.assertEquals;

@WithJenkins
class UserPropertyImplTest {

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void rsa() throws Exception {
        testRoundtrip(PUBLIC_RSA_KEY);
        testRoundtrip(PUBLIC_RSA_KEY_2);
    }

    @Test
    void dsa() throws Exception {
        testRoundtrip(PUBLIC_DSA_KEY);
    }

    private User configRoundtrip(User u) throws Exception {
        try {
            submit(r.createWebClient().goTo(u.getUrl() + "/security/").getFormByName("config"));
        } catch (FailingHttpStatusCodeException e) {
            // prior to https://github.com/jenkinsci/jenkins/pull/7268
            if (e.getStatusCode() == 404) {
                r.configRoundtrip(u);
            } else {
                throw e;
            }
        }
        return u;
    }

    private void testRoundtrip(String publicKey) throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        User foo = User.getById("foo", true);
        foo.addProperty(new UserPropertyImpl(publicKey));
        configRoundtrip(foo);
        assertEquals(publicKey, foo.getProperty(UserPropertyImpl.class).authorizedKeys);
    }

    @Issue("JENKINS-16337")
    @Test
    void testDoCheckAuthorizedKeys() throws Exception {
        assertCheckOK(FormValidation.Kind.OK, "");
        assertCheckOK(FormValidation.Kind.OK, PUBLIC_DSA_KEY);
        assertCheckOK(FormValidation.Kind.OK, PUBLIC_RSA_KEY);
        assertCheckOK(FormValidation.Kind.OK, PUBLIC_DSA_KEY + "\r\n" + PUBLIC_RSA_KEY + "\n\n");
        assertCheckOK(FormValidation.Kind.WARNING, PRIVATE_RSA_KEY);
    }

    private void assertCheckOK(FormValidation.Kind kind, String value) throws Exception {
        FormValidation fv = r.jenkins.getDescriptorByType(UserPropertyImpl.DescriptorImpl.class).doCheckAuthorizedKeys(value);
        assertEquals(kind, fv.kind, "check of ‘" + value + "’: " + fv.renderHtml());
    }

    private static final String PUBLIC_RSA_KEY = "ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEAr+ZaQ/SI8xIr5BtMCh7gizoH/cVzEi8tCxwvHOu5eELzxl1FBwUH5/pRzMI31w1+WlYXBCYQSvcWgpLlAZn7VaJYCxUE9K9gMxLPmk81fUec8sFr5hSj6cPL3hWdk4CgdJ0M2Q/GNJExvbDsiFMFb/p9jnrKhHQ47mhT4HpMLTE4fG5+AB3liJZhaUo9lbHfmhpmpps9o1tE1z7YcIO4ckvCklxF+04mVRjKur3lcezh2i4TXjMGmkDgU7pTrwf9OM9rDo5dSpsAK/dGWlBT01jhv69wOfUitcYENAK07Tgyoti3pEYD3b2ugxQ0fe0LqoxFa//O540PjMhxEbmuQQ== xxx@yyy";

    private static final String PRIVATE_RSA_KEY =
            """
                    -----BEGIN RSA PRIVATE KEY-----
                    MIIEogIBAAKCAQEAr+ZaQ/SI8xIr5BtMCh7gizoH/cVzEi8tCxwvHOu5eELzxl1F
                    BwUH5/pRzMI31w1+WlYXBCYQSvcWgpLlAZn7VaJYCxUE9K9gMxLPmk81fUec8sFr
                    5hSj6cPL3hWdk4CgdJ0M2Q/GNJExvbDsiFMFb/p9jnrKhHQ47mhT4HpMLTE4fG5+
                    AB3liJZhaUo9lbHfmhpmpps9o1tE1z7YcIO4ckvCklxF+04mVRjKur3lcezh2i4T
                    XjMGmkDgU7pTrwf9OM9rDo5dSpsAK/dGWlBT01jhv69wOfUitcYENAK07Tgyoti3
                    pEYD3b2ugxQ0fe0LqoxFa//O540PjMhxEbmuQQIBIwKCAQA8TvpgcRkC4ail+rr8
                    J9f1OHfEuLm9F30oYW89HZ6s48FLUy2cAbmRXSNcJVT5RnR2vm5KkLUhBEI7ZZBY
                    Ug0Hatxbki2VuHjBDcOFXPxld6OGbjOfV4in61vXHVqY+OaOYbtDG1nmIyb/NVhp
                    QQkttPfZFCgtaa0eiiti6BoeHu+RRycl5DOVyLyE85WDhnC8AT1bpJk3PFDQjW0G
                    Ht9qC67S+Tbh2W0bdcm58SbM8C+rHIeiq+8IbUt73nsyCLJEv2vN5zpPdCGwjV4r
                    1BPMzsgB0LOzniCRQmhHw6VmqPNhLCT/CHY3741iwxuOhIoXZaeC9EvCcWxZ5/PK
                    kpJLAoGBANVu1bcYskA9cy81hXcjMUjVlGt2ZO3vLrCTqrrS2DkXyzNo2UGv0uGp
                    FeVkqwz75/EDE3V/sMT3E2VIu9AC6k3irofLMnmZayyXuIw+SWuCyGfmYxKOjxk5
                    9u4JYXJgZTmqtPyfmF/c7K4oiSvbLX5Qr5FnRoEyDrDMOuAmLyG1AoGBANL7M+oO
                    PLmA7wxaqP0IOHigLQg6QOYoZ1M4okpL0XO6iSaXgXmOFnEb61j3vidK8xB5XLzu
                    t3Mf7rQ3CvGvhFINnT4qzs70HRv797zG3Fk7NV0pVlGKLip+zWYD+/V2u4hyDaRy
                    KkszAPlP0fhDEk/q9DYcG3C+XjgPqQnctGHdAoGBAJJaoS0YP7cFkM/qL6ImwrWZ
                    xNv5ace59CFPUIAbjPPzDv6uS9VFXWeJ4yD0koyPenlhMeorrGnN/qvaGmLAK6Mf
                    GJehRy7PmfKxLhcGI7dvn10wQ+93spT0jBDwfVW+cUwdSOe95NQFNJSuFOrfb6cS
                    wYhG0UKl+3HrIQ67GQErAoGAZnoDRcxmoz6f/q+xKnG1B2O+GfBoqk4jjtJddIs5
                    2SAWuvkhoXDmVDIh2sF5ng52D1Dj5r0XRovaV4hzB60FwXRTsHsxP/LpkT/eusb9
                    T+mO8rxOf2BfkPvC2cdrwF491I8rMp3aB0Sos5vMYqQ8GDBKuzI5NsLdTm4BpbRX
                    nT8CgYEAtY1KJQjGitOgMV8AiOgieUTRN8cR7z2bf9TUlZ3uHngP2NeR28g1EN1N
                    Qob9zCG3CPQmu7I3dWp1rDUu2ZickE7rISRfo2N9TXWlkJ7ZjhSmQ2gnYgPQ6YGU
                    LUNVNqTdfk2S8M+BM94pRqVgLSHHvwnqmMdoe7Ul3h2fk9CtNIw=
                    -----END RSA PRIVATE KEY-----""";

    private static final String PUBLIC_DSA_KEY = "ssh-dss AAAAB3NzaC1kc3MAAACBANmOhJjtmkkhF+Z9TTz1Y1/7pta/ZzNdY0h71T5DsD2WJb2cDGD+11oPxKiejCpDh4kQ5lDBUIHAfIcCoaFFkr85G89H5wTfoBethwmVnmVIzxUwGDh4VKMDF+meNlNh26a0h/0e00lOodIJvUz/2u7U7KTVrSrgtSZkAOLIWxK7AAAAFQDlI+2Ug32bB3xWpKmF5DqW67F82QAAAIAR85ga/Cz2wlvJSPqIxqm3ZS9LY5jvubA0mYH1XwYRZEWfYcI0j5NAfUCdv2RncFdeyo6ZIcREtu1uLU8rTqIcucgcRjMdrgDreN+ImyQKDkwH160if+PbsulG7bCZnl01Pp5YegUuAQknEqtg6cJg3N6is6BlsHv3elNzZITTsAAAAIEAinzZ44EogFDIajB/SqZ2xaJRubePnJuMXxjDh0RypZHQMNYKsf8NdE6ocrKMHw2Etg9CSZyaATpAuBZ3oNipuS+uJCk+i9Oc5oom8umowTUE7aGZtDnIMRBlL/MyOUPwoBNohUhSWDkI+CCu9qUhz160Q3ErYztyyB3CVaFBNSk= xxx@yyy";

    private static final String PRIVATE_DSA_KEY =
            """
                    -----BEGIN DSA PRIVATE KEY-----
                    MIIBvAIBAAKBgQDZjoSY7ZpJIRfmfU089WNf+6bWv2czXWNIe9U+Q7A9liW9nAxg
                    /tdaD8SonowqQ4eJEOZQwVCBwHyHAqGhRZK/ORvPR+cE36AXrYcJlZ5lSM8VMBg4
                    eFSjAxfpnjZTYdumtIf9HtNJTqHSCb1M/9ru1Oyk1a0q4LUmZADiyFsSuwIVAOUj
                    7ZSDfZsHfFakqYXkOpbrsXzZAoGAEfOYGvws9sJbyUj6iMapt2UvS2OY77mwNJmB
                    9V8GEWRFn2HCNI+TQH1Anb9kZ3BXXsqOmSHERLbtbi1PK06iHLnIHEYzHa4A63jf
                    iJskCg5MB9etIn/j27LpRu2wmZ5dNT6eWHoFLgEJJxKrYOnCYNzeorOgZbB793pT
                    c2SE07ACgYEAinzZ44EogFDIajB/SqZ2xaJRubePnJuMXxjDh0RypZHQMNYKsf8N
                    dE6ocrKMHw2Etg9CSZyaATpAuBZ3oNipuS+uJCk+i9Oc5oom8umowTUE7aGZtDnI
                    MRBlL/MyOUPwoBNohUhSWDkI+CCu9qUhz160Q3ErYztyyB3CVaFBNSkCFQDlBLXW
                    2eADfc6ZtDWcqfGCGbyvJg==
                    -----END DSA PRIVATE KEY-----""";

    private static final String PUBLIC_RSA_KEY_2 = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDJOrAWqnlbDZOv/nYPuGF07CDNgYFSrGs4Wilr6SM0QvVirBv6YClRZn4PfM30+6uFRxVR95hVeJhYUnSQbvYcglrAv/QFHmhjFEl+6Yrxn+wDMAvF2Fzk7m3AROuv2BTdzG28Xmgk0/Qj0eJyQA54L5k2mIMghuWuG/FeX9DCqtH78a81vxMCmP6vFcR82n43r2IaefHNafUV4p44BanCgEDFk2ioFAAVpwXvF9CejKDJ6QLGT62zY7lnO/yInXSxV/7HZDv2vEC2xzTE4aFlsEDNuYVPPQ8TiJa6j9WjVzqYzu791qb4XEHY2o83Dtb7h6IKcRWSJBmWvGa4a8rv your_email@example.com\n";
}
