package io.quarkiverse.cxf.it.security.policy;

import io.smallrye.certs.Format;
import io.smallrye.certs.junit5.Alias;
import io.smallrye.certs.junit5.Certificate;
import io.smallrye.certs.junit5.Certificates;

@Certificates(
        baseDir = "target/classes",
        certificates = @Certificate(
                name = "localhost-keystore",
                password = "localhost-keystore-password",
                aliases = @Alias(name = "localhost", password = "localhost-keystore-password"),
                formats = {
                        Format.PKCS12, Format.JKS
                }))
public abstract class AbstractSecurityPolicyTest {

}
