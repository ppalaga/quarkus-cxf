package io.quarkiverse.cxf.it.security.policy;

import java.util.Properties;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

import org.apache.wss4j.common.crypto.Crypto;
import org.apache.wss4j.common.crypto.CryptoFactory;
import org.apache.wss4j.common.crypto.PasswordEncryptor;
import org.apache.wss4j.common.ext.WSSecurityException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class CryptoProducers {

    private static final PasswordEncryptor dummyPasswordEncryptor = new PasswordEncryptor() {

        @Override
        public String encrypt(String password) {
            return password;
        }

        @Override
        public String decrypt(String encryptedPassword) {
            return encryptedPassword;
        }
    };

    @ConfigProperty(name = "keystore.type")
    String keystoreType;

    @Produces
    @ApplicationScoped
    @Named
    public Crypto bobCrypto() {
        return createCrypto(keystoreType, "bob", "bob-keystore-password", "bob-keystore." + keystoreType);
    }

    @Produces
    @ApplicationScoped
    @Named
    public Crypto aliceCrypto() {
        return createCrypto(keystoreType, "alice", "alice-keystore-password", "alice-keystore." + keystoreType);
    }

    public static Crypto createCrypto(String type, String alias, String password, String keyStoreFile) {
        Properties props = new Properties();
        props.put("org.apache.ws.security.crypto.provider", "org.apache.ws.security.components.crypto.Merlin");
        props.put("org.apache.ws.security.crypto.merlin.keystore.type", type);
        props.put("org.apache.ws.security.crypto.merlin.keystore.password", password);
        props.put("org.apache.ws.security.crypto.merlin.keystore.alias", alias);
        props.put("org.apache.ws.security.crypto.merlin.file", keyStoreFile);
        try {
            return CryptoFactory.getInstance(props, CryptoFactory.class.getClassLoader(), dummyPasswordEncryptor);
        } catch (WSSecurityException e) {
            throw new RuntimeException(e);
        }
    }

}
