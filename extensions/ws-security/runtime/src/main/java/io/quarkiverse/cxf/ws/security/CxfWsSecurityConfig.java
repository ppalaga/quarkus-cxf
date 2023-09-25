package io.quarkiverse.cxf.ws.security;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocIgnore;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "quarkus.cxf")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface CxfWsSecurityConfig {

    /**
     * Configure client proxies.
     */
    @WithName("client")
    Map<String, ClientConfig> clients();

    default boolean isClientPresent(String key) {
        return Optional.ofNullable(clients()).map(m -> m.containsKey(key)).orElse(false);
    }

    default ClientConfig getClient(String key) {
        return Optional.ofNullable(clients()).map(m -> m.get(key)).orElse(null);
    }


    /**
     * A class that provides configurable options of a CXF client.
     */
    @ConfigGroup
    interface ClientConfig {
        WsSecurityClientConfig wsSecurity();
    }


    /**
     * A class that provides configurable options of a CXF client.
     */
    @ConfigGroup
    interface WsSecurityClientConfig {
        /**
         * A comma separated list of WS-Security actions to perform. The following actions are supported:
         * <ul>
         * <li>{@code UsernameToken} - Perform a UsernameToken action
         * <li>{@code UsernameTokenSignature} - Perform a UsernameTokenSignature action
         * <li>{@code UsernameTokenNoPassword} - Perform a UsernameToken action with no password.
         * <li>{@code SAMLTokenUnsigned} - Perform an unsigned SAML Token action.
         * <li>{@code SAMLTokenSigned} - Perform a signed SAML Token action.
         * <li>{@code Signature} - Perform a Signature action. The signature specific parameters define how to sign, which keys to use, and so on.
         * <li>{@code Encryption} - Perform an Encryption action. The encryption specific parameters define how to encrypt, which keys to use, and so on.
         * <li>{@code Timestamp} - Add a timestamp to the security header.
         * <li>{@code SignatureDerived} - Perform a Signature action with derived keys. The signature specific parameters define how to sign, which keys to use, and so on.
         * <li>{@code EncryptionDerived} - Perform an Encryption action with derived keys. The encryption specific parameters define how to encrypt, which keys to use, and so on.
         * <li>{@code SignatureWithKerberosToken} - Perform a Signature action with a kerberos token. The signature specific parameters define how to sign, which keys to use, and so on.
         * <li>{@code EncryptionWithKerberosToken} - Perform a Encryption action with a kerberos token. The signature specific parameters define how to encrypt, which keys to use, and so on.
         * <li>{@code KerberosToken} - Add a kerberos token.
         * <li>{@code CustomToken} - Add a "Custom" token. This token will be retrieved from a CallbackHandler via {@code WSPasswordCallback.Usage.CUSTOM_TOKEN} and written out as is in the security header.
         * </ul>
         */
        Optional<List<WsSecurityAction>> actions();


        /**
         * The actor or role name of the <code>wsse:Security</code> header. If this parameter
         * is omitted, the actor name is not set.
         * <p/>
         * The value of the actor or role has to match the receiver's setting
         * or may contain standard values.
         */
        Optional<String> actor();

        /**
         * The user's name. It is used differently by each of the WS-Security functions.
         * <ul>
         * <li>The <i>UsernameToken</i> function sets this name in the
         * <code>UsernameToken</code>.
         * </li>
         * <li>The <i>Signing</i> function uses this name as the alias name
         * in the keystore to get user's certificate and private key to
         * perform signing if {@link #SIGNATURE_USER} is not used.
         * </li>
         * <li>The <i>encryption</i>
         * functions uses this parameter as fallback if {@link #ENCRYPTION_USER}
         * is not used.
         * </li>
         * </ul>
         */
        Optional<String> user();

        /**
         * The user's name for encryption. The encryption functions use the public key of
         * this user's certificate to encrypt the generated symmetric key.
         * <p/>
         * If this parameter is not set, then the encryption
         * function falls back to the {@link #USER} parameter to get the
         * certificate.
         * <p/>
         * If <b>only</b> encryption of the SOAP body data is requested,
         * it is recommended to use this parameter to define the username.
         */
        Optional<String> encryptionUser();

        /**
         * The user's name for signature. This name is used as the alias name in the keystore
         * to get user's certificate and private key to perform signing.
         * <p/>
         * If this parameter is not set, then the signature
         * function falls back to the {@link #USER} parameter.
         */
        Optional<String> signatureUser();


        /**
         * Specifying this name as {@link #ENCRYPTION_USER}
         * triggers a special action to get the public key to use for encryption.
         * <p/>
         * The handler uses the public key of the sender's certificate. Using this
         * way to define an encryption key simplifies certificate management to
         * a large extent.
         */
        Optional<String> useReqSigCert();

        /**
         * A {@code javax.security.auth.callback.CallbackHandler} implementation object used to obtain passwords.
         * Can be one of the following:
         * <ul>
         * <li>A fully qualified class name implementing {@code javax.security.auth.callback.CallbackHandler} to look up in the CDI
         * container.
         * <li>A bean name prefixed with {@code #} that will be looked up in the CDI container; example:
         * {@code #myCallbackHandler}
         * </ul>
         * The callback function
         * {@code CallbackHandler.handle(Callback[])} gets an array of
         * {@code org.apache.wss4j.common.ext.WSPasswordCallback} objects. Only the first entry of the array is used.
         * This object contains the username/keyname as identifier. The callback handler must set the password or key
         * associated with this identifier before it returns.
         */
        Optional<String> passwordCallback();

    }

    public enum WsSecurityAction {
        UsernameToken,
        UsernameTokenSignature,
        UsernameTokenNoPassword,
        SAMLTokenUnsigned,
        SAMLTokenSigned,
        Signature,
        Encryption,
        Timestamp,
        SignatureDerived,
        EncryptionDerived,
        SignatureWithKerberosToken,
        EncryptionWithKerberosToken,
        KerberosToken,
        CustomToken
    }

}
