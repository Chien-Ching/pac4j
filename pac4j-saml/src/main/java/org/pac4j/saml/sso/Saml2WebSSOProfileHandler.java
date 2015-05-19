/*
  Copyright 2012 -2014 Michael Remond

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package org.pac4j.saml.sso;

import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.xml.ParserPool;
import org.apache.velocity.app.VelocityEngine;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.decoder.MessageDecoder;
import org.opensaml.messaging.decoder.MessageDecodingException;
import org.opensaml.messaging.encoder.MessageEncoder;
import org.opensaml.messaging.encoder.MessageEncodingException;
import org.opensaml.profile.context.ProfileRequestContext;
import org.opensaml.saml.common.binding.SAMLBindingSupport;
import org.opensaml.saml.common.messaging.SAMLMessageSecuritySupport;
import org.opensaml.saml.common.messaging.context.SAMLEndpointContext;
import org.opensaml.saml.common.messaging.context.SAMLPeerEntityContext;
import org.opensaml.saml.common.messaging.context.SAMLSelfEntityContext;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.binding.encoding.impl.BaseSAML2MessageEncoder;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPPostEncoder;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPRedirectDeflateEncoder;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.xmlsec.EncryptionParameters;
import org.opensaml.xmlsec.keyinfo.KeyInfoGenerator;
import org.opensaml.xmlsec.keyinfo.impl.BasicKeyInfoGeneratorFactory;
import org.opensaml.xmlsec.signature.support.SignatureTrustEngine;
import org.pac4j.core.context.J2EContext;
import org.pac4j.saml.context.ExtendedSAMLMessageContext;
import org.pac4j.saml.crypto.CredentialProvider;
import org.pac4j.saml.exceptions.SamlException;
import org.pac4j.saml.util.VelocityEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.ws.EndpointContext;

/**
 * Handler capable of sending and receiving SAML messages according to the SAML2 SSO Browser profile.
 * 
 * @author Michael Remond
 * @since 1.5.0
 */
@SuppressWarnings("rawtypes")
public class Saml2WebSSOProfileHandler {

    private final static Logger logger = LoggerFactory.getLogger(Saml2WebSSOProfileHandler.class);

    private final CredentialProvider credentialProvider;

    private final MessageDecoder decoder;

    private final ParserPool parserPool;
    
    private String destinationBindingType;

    // SAML2 SSO browser profile because not available in opensaml constants
    public static final String SAML2_WEBSSO_PROFILE_URI = "urn:oasis:names:tc:SAML:2.0:profiles:SSO:browser";

    public Saml2WebSSOProfileHandler(final CredentialProvider credentialProvider,
            final MessageDecoder decoder, final ParserPool parserPool, String destinationBindingType) {
        this.credentialProvider = credentialProvider;
        this.decoder = decoder;
        this.parserPool = parserPool;
        this.destinationBindingType = destinationBindingType;
    }

    @SuppressWarnings("unchecked")
    public void sendMessage(final ExtendedSAMLMessageContext context, final AuthnRequest authnRequest, final String relayState) {

        final SPSSODescriptor spDescriptor = context.getSPSSODescriptor();
        final IDPSSODescriptor idpssoDescriptor = context.getIDPSSODescriptor();

        SingleSignOnService ssoService = context.getIDPSingleSignOnService(destinationBindingType);

        final MessageEncoder encoder = getMessageEncoder(context);

        final ProfileRequestContext request = context.getProfileRequestContext();
        request.setProfileId(SAML2_WEBSSO_PROFILE_URI);

        final ExtendedSAMLMessageContext outboundContext = new ExtendedSAMLMessageContext();
        outboundContext.setMessage(authnRequest);
        outboundContext.getSAMLPeerEndpointContext().setEndpoint(ssoService);

        if (relayState != null) {
            outboundContext.getSAMLBindingContext().setRelayState(relayState);
        }

        if (spDescriptor.isAuthnRequestsSigned()) {
            final EncryptionParameters params = new EncryptionParameters();
            params.setKeyTransportEncryptionCredential(this.credentialProvider.getCredential());
            outboundContext.getSecurityParametersContext().setEncryptionParameters(params);
        } else if (idpssoDescriptor.getWantAuthnRequestsSigned()) {
            logger.warn("IdP wants authn requests signed, it will perhaps reject your authn requests unless you provide a keystore");
        }

        try {
            encoder.setMessageContext(outboundContext);
            encoder.initialize();
            encoder.encode();
        } catch (final MessageEncodingException e) {
            throw new SamlException("Error encoding saml message", e);
        } catch (ComponentInitializationException e) {
            throw new SamlException("Error initializing saml encoder", e);
        }

    }

    private MessageEncoder getMessageEncoder(final ExtendedSAMLMessageContext ctx) {
        // Build the WebSSO handler for sending and receiving SAML2 messages
        BaseSAML2MessageEncoder encoder;
        if (SAMLConstants.SAML2_POST_BINDING_URI.equals(destinationBindingType)) {
            // Get a velocity engine for the HTTP-POST binding (building of an HTML document)
            VelocityEngine velocityEngine = VelocityEngineFactory.getEngine();
            encoder = new HTTPPostEncoder();
        } else if (SAMLConstants.SAML2_REDIRECT_BINDING_URI.equals(destinationBindingType)) {
            encoder = new HTTPRedirectDeflateEncoder();
        } else {
            throw new UnsupportedOperationException("Binding type - " + destinationBindingType + " is not supported");
        }

        encoder.setHttpServletResponse(ctx.getProfileRequestContextOutboundMessageTransportResponse());
        return encoder;
    }

    public void receiveMessage(final ExtendedSAMLMessageContext context, final SignatureTrustEngine engine) {

        final SAMLPeerEntityContext peerContext = context.getSAMLPeerEntityContext();

        peerContext.setRole(IDPSSODescriptor.DEFAULT_ELEMENT_NAME);
        context.getSAMLSelfProtocolContext().setProtocol(SAMLConstants.SAML20P_NS);

        try {
            final BasicKeyInfoGeneratorFactory factory = new BasicKeyInfoGeneratorFactory();
            final KeyInfoGenerator keyInfoGenerator = factory.newInstance();
            SAMLMessageSecuritySupport.getContextSigningParameters(context).setKeyInfoGenerator(keyInfoGenerator);
            decoder.decode();
        } catch (MessageDecodingException e) {
            throw new SamlException("Error decoding saml message", e);
        }

        final EntityDescriptor metadata = context.getSAMLPeerMetadataContext().getEntityDescriptor();
        if (metadata == null) {
            throw new SamlException("IDP Metadata cannot be null");
        }

        peerContext.setEntityId(metadata.getEntityID());
        context.getProfileRequestContext().setProfileId(SAML2_WEBSSO_PROFILE_URI);
    }

}
