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

package org.pac4j.saml.context;

import java.util.ArrayList;
import java.util.List;

import org.opensaml.messaging.context.MessageContext;
import org.opensaml.profile.context.ProfileRequestContext;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.messaging.context.SAMLBindingContext;
import org.opensaml.saml.common.messaging.context.SAMLEndpointContext;
import org.opensaml.saml.common.messaging.context.SAMLMetadataContext;
import org.opensaml.saml.common.messaging.context.SAMLPeerEntityContext;
import org.opensaml.saml.common.messaging.context.SAMLProtocolContext;
import org.opensaml.saml.common.messaging.context.SAMLSelfEntityContext;
import org.opensaml.saml.common.messaging.context.SAMLSubjectNameIdentifierContext;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.BaseID;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.xmlsec.context.SecurityParametersContext;
import org.pac4j.core.client.RedirectAction;
import org.pac4j.saml.exceptions.SamlException;
import org.pac4j.saml.transport.SimpleRequestAdapter;
import org.pac4j.saml.transport.SimpleResponseAdapter;

/**
 * Allow to store additional information for SAML processing.
 * 
 * @author Michael Remond
 * @version 1.5.0
 */
@SuppressWarnings("rawtypes")
public class ExtendedSAMLMessageContext extends MessageContext<SAMLObject> {

    public ExtendedSAMLMessageContext() {

    }

    public ExtendedSAMLMessageContext(MessageContext<SAMLObject> ctx) {
        super.setParent(ctx);
    }

    /* valid subject assertion */
    private Assertion subjectAssertion;

    /* id of the authn request */
    private String requestId;

    /* endpoint location */
    private String assertionConsumerUrl;

    /** BaseID retrieved either from the Subject or from a SubjectConfirmation */
    private BaseID baseID;
    
    /** SubjectConfirmations used during assertion evaluation. */
    private List<SubjectConfirmation> subjectConfirmations = new ArrayList<SubjectConfirmation>();
    
    private MetadataResolver metadataProvider;

    public Assertion getSubjectAssertion() {
        return this.subjectAssertion;
    }

    public SPSSODescriptor getSPSSODescriptor() {
        final SAMLMetadataContext selfContext = getSAMLSelfMetadataContext();
        final SPSSODescriptor spDescriptor = (SPSSODescriptor) selfContext.getRoleDescriptor();
        return spDescriptor;
    }

    public IDPSSODescriptor getIDPSSODescriptor() {
        final SAMLMetadataContext peerContext = getSAMLPeerMetadataContext();
        final IDPSSODescriptor idpssoDescriptor = (IDPSSODescriptor) peerContext.getRoleDescriptor();
        return idpssoDescriptor;
    }

    public SingleSignOnService getIDPSingleSignOnService(final String binding) {
        List<SingleSignOnService> services = getIDPSSODescriptor().getSingleSignOnServices();
        for (SingleSignOnService service : services) {
            if (service.getBinding().equals(binding)) {
                return service;
            }
        }
        throw new SamlException("Identity provider has no single sign on service available for the selected profile"
                + getIDPSSODescriptor());
    }


    public AssertionConsumerService getSPAssertionConsumerService() {
        return getSPAssertionConsumerService(null);
    }

    public AssertionConsumerService getSPAssertionConsumerService(final String acsIndex) {
        final SPSSODescriptor spssoDescriptor = getSPSSODescriptor();
        final List<AssertionConsumerService> services = spssoDescriptor.getAssertionConsumerServices();

        // Get by index
        if (acsIndex != null) {
            for (AssertionConsumerService service : services) {
                if (acsIndex.equals(service.getIndex())) {
                    return service;
                }
            }
            throw new SamlException("Assertion consumer service with index " + acsIndex
                    + " could not be found for spDescriptor " + spssoDescriptor);
        }

        // Get default
        if (spssoDescriptor.getDefaultAssertionConsumerService() != null) {
            return spssoDescriptor.getDefaultAssertionConsumerService();
        }

        // Get first
        if (services.size() > 0) {
            return services.iterator().next();
        }

        throw new SamlException("No assertion consumer services could be found for " + spssoDescriptor);
    }

    public ProfileRequestContext getProfileRequestContext() {
        return this.getSubcontext(ProfileRequestContext.class, true);
    }

    public SAMLSelfEntityContext getSAMLSelfEntityContext() {
        return this.getSubcontext(SAMLSelfEntityContext.class, true);
    }

    public SAMLMetadataContext getSAMLSelfMetadataContext() {
        return getSAMLSelfEntityContext().getSubcontext(SAMLMetadataContext.class, true);
    }

    public SAMLMetadataContext getSAMLPeerMetadataContext() {
        return getSAMLPeerEntityContext().getSubcontext(SAMLMetadataContext.class, true);
    }


    public SAMLMetadataContext getSAMLMetadataContext() {
        return this.getSubcontext(SAMLMetadataContext.class, true);
    }

    public SAMLPeerEntityContext getSAMLPeerEntityContext() {
        return this.getSubcontext(SAMLPeerEntityContext.class, true);
    }

    public SAMLSubjectNameIdentifierContext getSAMLSubjectNameIdentifierContext() {
        return this.getSubcontext(SAMLSubjectNameIdentifierContext.class, true);
    }

    public void setSubjectAssertion(final Assertion subjectAssertion) {
        this.subjectAssertion = subjectAssertion;
    }

    public String getRequestId() {
        return this.requestId;
    }

    public void setRequestId(final String requestId) {
        this.requestId = requestId;
    }

    public String getAssertionConsumerUrl() {
        return this.assertionConsumerUrl;
    }

    public void setAssertionConsumerUrl(final String assertionConsumerUrl) {
        this.assertionConsumerUrl = assertionConsumerUrl;
    }
    
    public BaseID getBaseID() {
        return baseID;
    }
    
    public void setBaseID(BaseID baseID) {
        this.baseID = baseID;
    }

    public List<SubjectConfirmation> getSubjectConfirmations() {
        return subjectConfirmations;
    }
    
    public void setSubjectConfirmations(List<SubjectConfirmation> subjectConfirmations) {
        this.subjectConfirmations = subjectConfirmations;
    }

    public void setMetadataProvider(MetadataResolver metadataProvider) {
        this.metadataProvider = metadataProvider;
    }

    public SAMLEndpointContext getSAMLPeerEndpointContext() {
        return getSAMLPeerEntityContext().getSubcontext(SAMLEndpointContext.class, true);
    }

    public SAMLBindingContext getSAMLBindingContext() {
        return this.getSubcontext(SAMLBindingContext.class, true);
    }

    public SecurityParametersContext getSecurityParametersContext() {
        return this.getSubcontext(SecurityParametersContext.class, true);
    }

    public SAMLProtocolContext getSAMLSelfProtocolContext() {
        return this.getSAMLSelfEntityContext().getSubcontext(SAMLProtocolContext.class, true);
    }

    public SimpleResponseAdapter getProfileRequestContextOutboundMessageTransportResponse() {
        return (SimpleResponseAdapter) getProfileRequestContext().getOutboundMessageContext().getMessage();
    }

    public SimpleRequestAdapter getProfileRequestContextInboundMessageTransportRequest() {
        return (SimpleRequestAdapter) getProfileRequestContext().getInboundMessageContext().getMessage();
    }

    public SAMLEndpointContext getSAMLEndpointContext() {
        return this.getSubcontext(SAMLEndpointContext.class, true);
    }
}
